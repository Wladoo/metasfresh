package de.metas.acct.gljournal_sap.service;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import de.metas.acct.api.AccountId;
import de.metas.acct.api.AcctSchemaId;
import de.metas.acct.api.PostingType;
import de.metas.acct.gljournal_sap.PostingSign;
import de.metas.acct.gljournal_sap.SAPGLJournal;
import de.metas.acct.gljournal_sap.SAPGLJournalCurrencyConversionCtx;
import de.metas.acct.gljournal_sap.SAPGLJournalId;
import de.metas.acct.gljournal_sap.SAPGLJournalLine;
import de.metas.acct.gljournal_sap.SAPGLJournalLineId;
import de.metas.acct.model.I_SAP_GLJournal;
import de.metas.acct.model.I_SAP_GLJournalLine;
import de.metas.currency.FixedConversionRate;
import de.metas.document.dimension.Dimension;
import de.metas.document.engine.DocStatus;
import de.metas.money.CurrencyConversionTypeId;
import de.metas.money.CurrencyId;
import de.metas.money.Money;
import de.metas.order.OrderId;
import de.metas.organization.ClientAndOrgId;
import de.metas.organization.OrgId;
import de.metas.product.ProductId;
import de.metas.product.acct.api.ActivityId;
import de.metas.sectionCode.SectionCodeId;
import de.metas.tax.api.TaxId;
import de.metas.util.Services;
import de.metas.util.StringUtils;
import de.metas.util.lang.SeqNo;
import lombok.NonNull;
import org.adempiere.ad.dao.IQueryBL;
import org.adempiere.ad.dao.IQueryBuilder;
import org.adempiere.exceptions.AdempiereException;
import org.adempiere.model.InterfaceWrapperHelper;
import de.metas.acct.Account;

import javax.annotation.Nullable;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

public class SAPGLJournalLoaderAndSaver
{
	private final IQueryBL queryBL = Services.get(IQueryBL.class);

	private final HashMap<SAPGLJournalId, I_SAP_GLJournal> headersById = new HashMap<>();
	private final HashSet<SAPGLJournalId> headerIdsToAvoidSaving = new HashSet<>();

	private final HashMap<SAPGLJournalId, ArrayList<I_SAP_GLJournalLine>> linesByHeaderId = new HashMap<>();

	public void addToCacheAndAvoidSaving(@NonNull final I_SAP_GLJournal record)
	{
		@NonNull final SAPGLJournalId glJournalId = extractId(record);
		headersById.put(glJournalId, record);
		headerIdsToAvoidSaving.add(glJournalId);
	}

	public SAPGLJournal getById(@NonNull final SAPGLJournalId id)
	{
		final I_SAP_GLJournal headerRecord = getHeaderRecordById(id);
		final List<I_SAP_GLJournalLine> lineRecords = getLineRecords(id);
		return fromRecord(headerRecord, lineRecords);
	}

	private I_SAP_GLJournal getHeaderRecordById(@NonNull final SAPGLJournalId id)
	{
		return getHeaderRecordByIdIfExists(id)
				.orElseThrow(() -> new AdempiereException("No SAP GL Journal found for " + id));
	}

	private Optional<I_SAP_GLJournal> getHeaderRecordByIdIfExists(@NonNull final SAPGLJournalId id)
	{
		return Optional.ofNullable(headersById.computeIfAbsent(id, this::retrieveHeaderRecordById));
	}

	@Nullable
	private I_SAP_GLJournal retrieveHeaderRecordById(@NonNull final SAPGLJournalId id)
	{
		return queryBL.createQueryBuilder(I_SAP_GLJournal.class)
				.addEqualsFilter(I_SAP_GLJournal.COLUMNNAME_SAP_GLJournal_ID, id)
				.create()
				.firstOnly(I_SAP_GLJournal.class);
	}

	public static SAPGLJournalCurrencyConversionCtx extractConversionCtx(@NonNull final I_SAP_GLJournal glJournal)
	{
		final CurrencyId currencyId = CurrencyId.ofRepoId(glJournal.getC_Currency_ID());
		final CurrencyId acctCurrencyId = CurrencyId.ofRepoId(glJournal.getAcct_Currency_ID());

		final FixedConversionRate fixedConversionRate;
		final BigDecimal currencyRateBD = glJournal.getCurrencyRate();
		if (currencyRateBD.signum() != 0)
		{
			fixedConversionRate = FixedConversionRate.builder()
					.fromCurrencyId(currencyId)
					.toCurrencyId(acctCurrencyId)
					.multiplyRate(currencyRateBD)
					.build();
		}
		else
		{
			fixedConversionRate = null;
		}

		return SAPGLJournalCurrencyConversionCtx.builder()
				.acctCurrencyId(acctCurrencyId)
				.currencyId(currencyId)
				.conversionTypeId(CurrencyConversionTypeId.ofRepoIdOrNull(glJournal.getC_ConversionType_ID()))
				.date(glJournal.getDateAcct().toInstant())
				.fixedConversionRate(fixedConversionRate)
				.clientAndOrgId(ClientAndOrgId.ofClientAndOrg(glJournal.getAD_Client_ID(), glJournal.getAD_Org_ID()))
				.build();
	}

	public ArrayList<I_SAP_GLJournalLine> getLineRecords(@NonNull final SAPGLJournalId glJournalId)
	{
		return linesByHeaderId.computeIfAbsent(glJournalId, this::retrieveLineRecords);
	}

	private ArrayList<I_SAP_GLJournalLine> retrieveLineRecords(final @NonNull SAPGLJournalId glJournalId)
	{
		return queryLinesByHeaderId(glJournalId)
				.create()
				.stream()
				.collect(Collectors.toCollection(ArrayList::new));

	}

	private IQueryBuilder<I_SAP_GLJournalLine> queryLinesByHeaderId(final @NonNull SAPGLJournalId glJournalId)
	{
		return queryBL.createQueryBuilder(I_SAP_GLJournalLine.class)
				.addInArrayFilter(I_SAP_GLJournalLine.COLUMNNAME_SAP_GLJournal_ID, glJournalId);
	}

	SeqNo getNextSeqNo(@NonNull final SAPGLJournalId glJournalId)
	{
		final int lastLineInt = queryLinesByHeaderId(glJournalId)
				.create()
				.maxInt(I_SAP_GLJournalLine.COLUMNNAME_Line);

		final SeqNo lastLineNo = SeqNo.ofInt(Math.max(lastLineInt, 0));
		return lastLineNo.next();
	}

	private static SAPGLJournal fromRecord(
			@NonNull final I_SAP_GLJournal headerRecord,
			@NonNull final List<I_SAP_GLJournalLine> lineRecords)
	{
		final SAPGLJournalCurrencyConversionCtx conversionCtx = extractConversionCtx(headerRecord);

		return SAPGLJournal.builder()
				.id(extractId(headerRecord))
				.conversionCtx(conversionCtx)
				.acctSchemaId(AcctSchemaId.ofRepoId(headerRecord.getC_AcctSchema_ID()))
				.postingType(PostingType.ofCode(headerRecord.getPostingType()))
				.lines(lineRecords.stream()
						.map(lineRecord -> fromRecord(lineRecord, conversionCtx))
						.sorted(Comparator.comparing(SAPGLJournalLine::getLine).thenComparing(SAPGLJournalLine::getIdNotNull))
						.collect(Collectors.toCollection(ArrayList::new)))
				.totalAcctDR(Money.of(headerRecord.getTotalDr(), conversionCtx.getAcctCurrencyId()))
				.totalAcctCR(Money.of(headerRecord.getTotalCr(), conversionCtx.getAcctCurrencyId()))
				.docStatus(DocStatus.ofCode(headerRecord.getDocStatus()))
				//
				.orgId(OrgId.ofRepoId(headerRecord.getAD_Org_ID()))
				.dimension(extractDimension(headerRecord))
				//
				.build();
	}

	private static Dimension extractDimension(final I_SAP_GLJournal headerRecord)
	{
		return Dimension.builder()
				.sectionCodeId(SectionCodeId.ofRepoIdOrNull(headerRecord.getM_SectionCode_ID()))
				.build();
	}

	private static SAPGLJournalLine fromRecord(
			@NonNull final I_SAP_GLJournalLine record,
			@NonNull final SAPGLJournalCurrencyConversionCtx conversionCtx)
	{
		return SAPGLJournalLine.builder()
				.id(extractId(record))
				.parentId(SAPGLJournalLineId.ofRepoIdOrNull(record.getSAP_GLJournal_ID(), record.getParent_ID()))
				//
				.line(SeqNo.ofInt(record.getLine()))
				.description(StringUtils.trimBlankToNull(record.getDescription()))
				//
				.account(Account.ofId(AccountId.ofRepoId(record.getC_ValidCombination_ID())))
				.postingSign(PostingSign.ofCode(record.getPostingSign()))
				.amount(Money.of(record.getAmount(), conversionCtx.getCurrencyId()))
				.amountAcct(Money.of(record.getAmtAcct(), conversionCtx.getAcctCurrencyId()))
				//
				.taxId(TaxId.ofRepoIdOrNull(record.getC_Tax_ID()))
				//
				.orgId(OrgId.ofRepoId(record.getAD_Org_ID()))
				.dimension(extractDimension(record))
				//
				.build();
	}

	private static Dimension extractDimension(final @NonNull I_SAP_GLJournalLine record)
	{
		return Dimension.builder()
				.sectionCodeId(SectionCodeId.ofRepoIdOrNull(record.getM_SectionCode_ID()))
				.productId(ProductId.ofRepoIdOrNull(record.getM_Product_ID()))
				.salesOrderId(OrderId.ofRepoIdOrNull(record.getC_OrderSO_ID()))
				.activityId(ActivityId.ofRepoIdOrNull(record.getC_Activity_ID()))
				.build();
	}

	@NonNull
	public static SAPGLJournalId extractId(final @NonNull I_SAP_GLJournal header)
	{
		return SAPGLJournalId.ofRepoId(header.getSAP_GLJournal_ID());
	}

	@NonNull
	private static SAPGLJournalLineId extractId(final @NonNull I_SAP_GLJournalLine line)
	{
		return SAPGLJournalLineId.ofRepoId(line.getSAP_GLJournal_ID(), line.getSAP_GLJournalLine_ID());
	}

	public void save(final SAPGLJournal glJournal)
	{
		final I_SAP_GLJournal headerRecord = getHeaderRecordById(glJournal.getId());
		updateHeaderRecord(headerRecord, glJournal);
		saveRecordIfAllowed(headerRecord);

		final ArrayList<I_SAP_GLJournalLine> lineRecords = getLineRecords(glJournal.getId());
		final ImmutableMap<SAPGLJournalLineId, I_SAP_GLJournalLine> lineRecordsById = Maps.uniqueIndex(lineRecords, SAPGLJournalLoaderAndSaver::extractId);

		//
		// NEW/UPDATE
		final HashSet<SAPGLJournalLineId> savedLineIds = new HashSet<>();
		for (SAPGLJournalLine line : glJournal.getLines())
		{
			SAPGLJournalLineId lineId = line.getIdOrNull();
			final I_SAP_GLJournalLine lineRecord;
			if (lineId != null)
			{
				lineRecord = lineRecordsById.get(lineId);
				if (lineRecord == null)
				{
					throw new AdempiereException("@NotFound@ " + lineId); // shall not happen
				}
			}
			else
			{
				lineRecord = InterfaceWrapperHelper.newInstance(I_SAP_GLJournalLine.class);
				lineRecord.setSAP_GLJournal_ID(headerRecord.getSAP_GLJournal_ID());
				lineRecord.setAD_Org_ID(headerRecord.getAD_Org_ID());
			}

			updateLineRecord(lineRecord, line);
			InterfaceWrapperHelper.save(lineRecord);
			lineId = extractId(lineRecord);
			line.markAsSaved(lineId);

			savedLineIds.add(lineId);
		}

		//
		// DELETE
		for (Iterator<I_SAP_GLJournalLine> it = lineRecords.iterator(); it.hasNext(); )
		{
			final I_SAP_GLJournalLine lineRecord = it.next();
			final SAPGLJournalLineId id = extractId(lineRecord);
			if (!savedLineIds.contains(id))
			{
				it.remove();
				InterfaceWrapperHelper.delete(lineRecord);
			}
		}
	}

	private static void updateHeaderRecord(final I_SAP_GLJournal headerRecord, final SAPGLJournal glJournal)
	{
		headerRecord.setTotalDr(glJournal.getTotalAcctDR().toBigDecimal());
		headerRecord.setTotalCr(glJournal.getTotalAcctCR().toBigDecimal());
	}

	private static void updateLineRecord(final I_SAP_GLJournalLine lineRecord, final SAPGLJournalLine line)
	{
		lineRecord.setParent_ID(SAPGLJournalLineId.toRepoId(line.getParentId()));
		lineRecord.setLine(line.getLine().toInt());
		lineRecord.setDescription(StringUtils.trimBlankToNull(line.getDescription()));
		lineRecord.setC_ValidCombination_ID(line.getAccount().getAccountId().getRepoId());
		lineRecord.setPostingSign(line.getPostingSign().getCode());
		lineRecord.setAmount(line.getAmount().toBigDecimal());
		lineRecord.setAmtAcct(line.getAmountAcct().toBigDecimal());
		lineRecord.setC_Tax_ID(TaxId.toRepoId(line.getTaxId()));

		lineRecord.setAD_Org_ID(line.getOrgId().getRepoId());
		updateLineRecordFromDimension(lineRecord, line.getDimension());
	}

	private static void updateLineRecordFromDimension(final I_SAP_GLJournalLine lineRecord, final Dimension dimension)
	{
		lineRecord.setM_SectionCode_ID(SectionCodeId.toRepoId(dimension.getSectionCodeId()));
		lineRecord.setM_Product_ID(ProductId.toRepoId(dimension.getProductId()));
		lineRecord.setC_OrderSO_ID(OrderId.toRepoId(dimension.getSalesOrderId()));
		lineRecord.setC_Activity_ID(ActivityId.toRepoId(dimension.getActivityId()));
	}

	private void saveRecordIfAllowed(final I_SAP_GLJournal headerRecord)
	{
		if (headerIdsToAvoidSaving.contains(extractId(headerRecord)))
		{
			return;
		}

		InterfaceWrapperHelper.save(headerRecord);
	}

	public void updateById(
			@NonNull final SAPGLJournalId id,
			@NonNull Consumer<SAPGLJournal> consumer)
	{
		updateById(
				id,
				glJournal -> {
					consumer.accept(glJournal);
					return null; // N/A
				});
	}

	public <R> R updateById(
			@NonNull final SAPGLJournalId id,
			@NonNull Function<SAPGLJournal, R> processor)
	{
		final SAPGLJournal glJournal = getById(id);
		final R result = processor.apply(glJournal);
		save(glJournal);

		return result;
	}

	public DocStatus getDocStatus(final SAPGLJournalId glJournalId)
	{
		return getHeaderRecordByIdIfExists(glJournalId)
				.map(headerRecord -> DocStatus.ofNullableCodeOrUnknown(headerRecord.getDocStatus()))
				.orElse(DocStatus.Unknown);
	}
}
