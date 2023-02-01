package de.metas.rest_api.v2.invoice.impl;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import de.metas.RestUtils;
import de.metas.acct.accounts.ProductAcctType;
import de.metas.acct.api.AcctSchema;
import de.metas.acct.api.ChartOfAccountsId;
import de.metas.acct.api.IAcctSchemaDAO;
import de.metas.acct.api.impl.ElementValueId;
import de.metas.acct.vatcode.IVATCodeDAO;
import de.metas.adempiere.model.I_C_InvoiceLine;
import de.metas.banking.BankAccountId;
import de.metas.banking.payment.paymentallocation.InvoiceToAllocate;
import de.metas.banking.payment.paymentallocation.InvoiceToAllocateQuery;
import de.metas.banking.payment.paymentallocation.PaymentAllocationRepository;
import de.metas.banking.payment.paymentallocation.PaymentToAllocate;
import de.metas.banking.payment.paymentallocation.PaymentToAllocateQuery;
import de.metas.banking.payment.paymentallocation.service.AllocationAmounts;
import de.metas.banking.payment.paymentallocation.service.PayableDocument;
import de.metas.banking.payment.paymentallocation.service.PaymentAllocationBuilder;
import de.metas.banking.payment.paymentallocation.service.PaymentDocument;
import de.metas.bpartner.BPartnerId;
import de.metas.bpartner.service.BPartnerInfo;
import de.metas.common.ordercandidates.v2.request.JsonRequestBPartnerLocationAndContact;
import de.metas.common.rest_api.common.JsonExternalId;
import de.metas.common.rest_api.common.JsonMetasfreshId;
import de.metas.common.rest_api.v2.JsonDocTypeInfo;
import de.metas.common.rest_api.v2.invoice.JsonInvoicePaymentCreateRequest;
import de.metas.common.rest_api.v2.invoice.JsonPaymentAllocationLine;
import de.metas.common.rest_api.v2.invoice.JsonPaymentDirection;
import de.metas.common.util.CoalesceUtil;
import de.metas.common.util.time.SystemTime;
import de.metas.currency.ConversionTypeMethod;
import de.metas.currency.CurrencyCode;
import de.metas.currency.CurrencyConversionContext;
import de.metas.currency.ICurrencyBL;
import de.metas.document.DocBaseAndSubType;
import de.metas.document.DocBaseType;
import de.metas.document.DocTypeId;
import de.metas.document.engine.IDocument;
import de.metas.document.engine.IDocumentBL;
import de.metas.elementvalue.ElementValue;
import de.metas.elementvalue.ElementValueService;
import de.metas.externalreference.ExternalIdentifier;
import de.metas.inout.IInOutDAO;
import de.metas.invoice.InvoiceId;
import de.metas.invoice.InvoiceQuery;
import de.metas.invoice.InvoiceService;
import de.metas.invoice.ManualInvoice;
import de.metas.invoice.ManualInvoiceService;
import de.metas.invoice.invoiceProcessingServiceCompany.InvoiceProcessingFeeCalculation;
import de.metas.invoice.invoiceProcessingServiceCompany.InvoiceProcessingFeeComputeRequest;
import de.metas.invoice.invoiceProcessingServiceCompany.InvoiceProcessingServiceCompanyService;
import de.metas.invoice.request.CreateInvoiceRequest;
import de.metas.invoice.request.CreateInvoiceRequestHeader;
import de.metas.invoice.request.CreateInvoiceRequestLine;
import de.metas.invoice.service.IInvoiceDAO;
import de.metas.invoicecandidate.api.IInvoiceCandDAO;
import de.metas.invoicecandidate.model.I_C_Invoice_Candidate;
import de.metas.lang.SOTrx;
import de.metas.logging.LogManager;
import de.metas.money.CurrencyId;
import de.metas.money.Money;
import de.metas.money.MoneyService;
import de.metas.order.OrderId;
import de.metas.order.impl.DocTypeService;
import de.metas.organization.ClientAndOrgId;
import de.metas.organization.IOrgDAO;
import de.metas.organization.OrgId;
import de.metas.payment.PaymentAmtMultiplier;
import de.metas.payment.PaymentId;
import de.metas.payment.TenderType;
import de.metas.payment.api.DefaultPaymentBuilder;
import de.metas.product.IProductBL;
import de.metas.product.ProductId;
import de.metas.quantity.Quantitys;
import de.metas.rest_api.invoicecandidates.response.JsonInvoiceCandidatesResponseItem;
import de.metas.rest_api.invoicecandidates.response.JsonReverseInvoiceResponse;
import de.metas.rest_api.utils.CurrencyService;
import de.metas.rest_api.utils.IdentifierString;
import de.metas.rest_api.utils.MetasfreshId;
import de.metas.rest_api.v2.bpartner.bpartnercomposite.JsonRetrieverService;
import de.metas.rest_api.v2.bpartner.bpartnercomposite.JsonServiceFactory;
import de.metas.rest_api.v2.invoice.JsonCreateInvoiceLineItemRequest;
import de.metas.rest_api.v2.invoice.JsonCreateInvoiceRequest;
import de.metas.rest_api.v2.invoice.JsonCreateInvoiceRequestItem;
import de.metas.rest_api.v2.invoice.JsonCreateInvoiceRequestItemHeader;
import de.metas.rest_api.v2.ordercandidates.impl.MasterdataProvider;
import de.metas.rest_api.v2.ordercandidates.impl.ProductMasterDataProvider;
import de.metas.rest_api.v2.payment.PaymentService;
import de.metas.tax.api.ITaxDAO;
import de.metas.tax.api.TaxId;
import de.metas.tax.api.VatCodeId;
import de.metas.uom.IUOMDAO;
import de.metas.uom.UomId;
import de.metas.uom.X12DE355;
import de.metas.util.Check;
import de.metas.util.Services;
import de.metas.util.lang.ExternalId;
import de.metas.util.lang.Percent;
import de.metas.util.web.exception.MissingResourceException;
import lombok.NonNull;
import org.adempiere.ad.trx.api.ITrxManager;
import org.adempiere.archive.api.IArchiveBL;
import org.adempiere.exceptions.AdempiereException;
import org.adempiere.service.ClientId;
import org.adempiere.util.lang.impl.TableRecordReference;
import org.compiere.model.I_AD_Archive;
import org.compiere.model.I_C_Invoice;
import org.compiere.model.I_C_Payment;
import org.compiere.model.I_M_InOutLine;
import org.compiere.util.Env;
import org.slf4j.Logger;
import org.springframework.stereotype.Service;

import javax.annotation.Nullable;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

import static de.metas.RestUtils.retrieveOrgIdOrDefault;

/*
 * #%L
 * de.metas.business.rest-api-impl
 * %%
 * Copyright (C) 2019 metas GmbH
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 2 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public
 * License along with this program. If not, see
 * <http://www.gnu.org/licenses/gpl-2.0.html>.
 * #L%
 */

@Service
public class JsonInvoiceService
{
	private final static transient Logger logger = LogManager.getLogger(InvoiceService.class);

	private final IArchiveBL archiveBL = Services.get(IArchiveBL.class);
	private final IDocumentBL documentBL = Services.get(IDocumentBL.class);
	private final IInvoiceCandDAO invoiceCandDAO = Services.get(IInvoiceCandDAO.class);
	private final IInvoiceDAO invoiceDAO = Services.get(IInvoiceDAO.class);
	private final ITaxDAO taxDAO = Services.get(ITaxDAO.class);
	private final IProductBL productBL = Services.get(IProductBL.class);
	private final ICurrencyBL currencyBL = Services.get(ICurrencyBL.class);
	private final ITrxManager trxManager = Services.get(ITrxManager.class);
	private final IInOutDAO inOutDAO = Services.get(IInOutDAO.class);
	private final IAcctSchemaDAO acctSchemaDAO = Services.get(IAcctSchemaDAO.class);
	private final IOrgDAO orgDAO = Services.get(IOrgDAO.class);
	private final IVATCodeDAO vatCodeDAO = Services.get(IVATCodeDAO.class);
	private final IUOMDAO uomDAO = Services.get(IUOMDAO.class);

	private final CurrencyService currencyService;
	private final PaymentService paymentService;
	private final JsonRetrieverService jsonRetrieverService;
	private final InvoiceService invoiceService;
	private final ManualInvoiceService manualInvoiceService;
	private final DocTypeService docTypeService;
	private final ElementValueService elementValueService;
	private final PaymentAllocationRepository paymentAllocationRepository;
	private final MoneyService moneyService;
	private final InvoiceProcessingServiceCompanyService invoiceProcessingServiceCompanyService;

	public JsonInvoiceService(
			@NonNull final CurrencyService currencyService,
			@NonNull final PaymentService paymentService,
			@NonNull final JsonServiceFactory jsonServiceFactory,
			@NonNull final InvoiceService invoiceService,
			@NonNull final ManualInvoiceService manualInvoiceService,
			@NonNull final DocTypeService docTypeService,
			@NonNull final ElementValueService elementValueService,
			@NonNull final PaymentAllocationRepository paymentAllocationRepository,
			@NonNull final MoneyService moneyService,
			@NonNull final InvoiceProcessingServiceCompanyService invoiceProcessingServiceCompanyService)
	{
		this.currencyService = currencyService;
		this.paymentService = paymentService;
		this.jsonRetrieverService = jsonServiceFactory.createRetriever();
		this.invoiceService = invoiceService;
		this.manualInvoiceService = manualInvoiceService;
		this.docTypeService = docTypeService;
		this.elementValueService = elementValueService;
		this.paymentAllocationRepository = paymentAllocationRepository;
		this.moneyService = moneyService;
		this.invoiceProcessingServiceCompanyService = invoiceProcessingServiceCompanyService;
	}

	public Optional<byte[]> getInvoicePDF(@NonNull final InvoiceId invoiceId)
	{
		return getLastArchive(invoiceId).map(archiveBL::getBinaryData);
	}

	@NonNull
	public JSONInvoiceInfoResponse getInvoiceInfo(@NonNull final InvoiceId invoiceId, final String ad_language)
	{
		final JSONInvoiceInfoResponse.JSONInvoiceInfoResponseBuilder result = JSONInvoiceInfoResponse.builder();

		final I_C_Invoice invoice = invoiceDAO.getByIdInTrx(invoiceId);

		final CurrencyCode currency = currencyBL.getCurrencyCodeById(CurrencyId.ofRepoId(invoice.getC_Currency_ID()));

		final List<I_C_InvoiceLine> lines = invoiceDAO.retrieveLines(invoiceId);
		for (final I_C_InvoiceLine line : lines)
		{
			final String productName = productBL.getProductNameTrl(ProductId.ofRepoId(line.getM_Product_ID())).translate(ad_language);
			final Percent taxRate = taxDAO.getRateById(TaxId.ofRepoId(line.getC_Tax_ID()));

			result.lineInfo(JSONInvoiceLineInfo.builder()
									.lineNumber(line.getLine())
									.productName(productName)
									.qtyInvoiced(line.getQtyEntered())
									.price(line.getPriceEntered())
									.taxRate(taxRate)
									.lineNetAmt(line.getLineNetAmt())
									.currency(currency)
									.build());
		}

		result.invoiceId(JsonMetasfreshId.of(invoiceId.getRepoId()));

		return result.build();
	}

	@NonNull
	public Optional<JsonReverseInvoiceResponse> reverseInvoice(@NonNull final InvoiceId invoiceId)
	{
		final I_C_Invoice documentRecord = invoiceDAO.getByIdInTrx(invoiceId);
		if (documentRecord == null)
		{
			return Optional.empty();
		}

		documentBL.processEx(documentRecord, IDocument.ACTION_Reverse_Correct, IDocument.STATUS_Reversed);

		final JsonReverseInvoiceResponse.JsonReverseInvoiceResponseBuilder responseBuilder = JsonReverseInvoiceResponse.builder();

		invoiceCandDAO
				.retrieveInvoiceCandidates(invoiceId)
				.stream()
				.map(this::buildJSONItem)
				.forEach(responseBuilder::affectedInvoiceCandidate);

		return Optional.of(responseBuilder.build());
	}

	public void createPaymentFromJson(@NonNull final JsonInvoicePaymentCreateRequest request)
	{
		trxManager.runInThreadInheritedTrx(() -> createPaymentWithinTrx(request));
	}

	private void createPaymentWithinTrx(@NonNull final JsonInvoicePaymentCreateRequest request)
	{
		final List<JsonPaymentAllocationLine> lines = request.getLines();
		validateAllocationLineAmounts(lines);

		final OrgId orgId = getOrgId(request.getOrgCode());
		final CurrencyId currencyId = getCurrencyId(request.getCurrencyCode());

		final ZonedDateTime dateTrx = getDateTrx(request, orgId);

		final PaymentId paymentId = createPayment(request, orgId, currencyId, dateTrx);

		createAllocationsForPayment(paymentId,
									orgId,
									currencyId,
									dateTrx,
									request);
	}

	@NonNull
	public List<JSONInvoiceInfoResponse> buildInvoiceInfoResponseFromOrderIds(@NonNull final Set<OrderId> orderIds)
	{
		final List<I_M_InOutLine> shipmentLines = inOutDAO.retrieveShipmentLinesForOrderId(orderIds);

		final Set<InvoiceId> invoiceIds = invoiceService.retrieveInvoiceCandsByInOutLines(shipmentLines).stream()
				.map(invoiceCandDAO::retrieveIlForIc)
				.flatMap(List::stream)
				.map(org.compiere.model.I_C_InvoiceLine::getC_Invoice_ID)
				.map(InvoiceId::ofRepoId)
				.collect(ImmutableSet.toImmutableSet());

		return invoiceIds.stream()
				.map(invoiceId -> getInvoiceInfo(invoiceId, Env.getAD_Language()))
				.collect(ImmutableList.toImmutableList());
	}

	@NonNull
	public ManualInvoice createInvoice(
			@NonNull final JsonCreateInvoiceRequest request,
			@NonNull final MasterdataProvider masterdataProvider)
	{
		return trxManager.callInThreadInheritedTrx(() -> createInvoiceWithinTrx(request, masterdataProvider));
	}

	private JsonInvoiceCandidatesResponseItem buildJSONItem(@NonNull final I_C_Invoice_Candidate invoiceCandidate)
	{
		return JsonInvoiceCandidatesResponseItem
				.builder()
				.externalHeaderId(JsonExternalId.ofOrNull(invoiceCandidate.getExternalHeaderId()))
				.externalLineId(JsonExternalId.ofOrNull(invoiceCandidate.getExternalLineId()))
				.metasfreshId(MetasfreshId.of(invoiceCandidate.getC_Invoice_Candidate_ID()))
				.build();
	}

	private void createAllocationsForPayment(
			@NonNull final PaymentId paymentId,
			@NonNull final OrgId orgId,
			@NonNull final CurrencyId currencyId,
			@NonNull final ZonedDateTime dateTrx,
			@NonNull final JsonInvoicePaymentCreateRequest request)
	{
		final List<JsonPaymentAllocationLine> allocationLines = request.getLines();
		if (allocationLines == null)
		{
			return;
		}

		final ImmutableList.Builder<PayableDocument> payableDocumentsCollector = ImmutableList.builder();

		final Map<IdentifierString, InvoiceToAllocate> identifier2InvoiceToAllocate = new HashMap<>();

		allocationLines
				.forEach(line -> {
					final IdentifierString invoiceIdentifier = IdentifierString.of(line.getInvoiceIdentifier());

					final InvoiceToAllocate invoiceToAllocate = Optional.ofNullable(identifier2InvoiceToAllocate.get(invoiceIdentifier))
							.orElseGet(() -> getInvoiceToAllocate(invoiceIdentifier, orgId, dateTrx, getDocType(line)));

					validateInvoice(invoiceToAllocate, invoiceIdentifier, request.getType());

					payableDocumentsCollector.add(buildPayableDocument(invoiceToAllocate, dateTrx, currencyId, line));

					identifier2InvoiceToAllocate.putIfAbsent(invoiceIdentifier, invoiceToAllocate);
				});

		final ImmutableList<PayableDocument> payableDocuments = payableDocumentsCollector.build();
		final ImmutableList<PaymentDocument> paymentDocuments = ImmutableList.of(buildPaymentDocument(paymentId, dateTrx));

		PaymentAllocationBuilder.newBuilder()
				.invoiceProcessingServiceCompanyService(invoiceProcessingServiceCompanyService)
				.defaultDateTrx(dateTrx.toLocalDate())
				.paymentDocuments(paymentDocuments)
				.payableDocuments(payableDocuments)
				.allowPartialAllocations(true)
				.payableRemainingOpenAmtPolicy(PaymentAllocationBuilder.PayableRemainingOpenAmtPolicy.DO_NOTHING)
				.build();
	}

	@NonNull
	private InvoiceToAllocate getInvoiceToAllocate(
			@NonNull final IdentifierString invoiceIdentifier,
			@NonNull final OrgId orgId,
			@NonNull final ZonedDateTime dateTrx,
			@Nullable final DocBaseAndSubType docType)
	{
		final InvoiceId invoiceId = retrieveInvoiceId(invoiceIdentifier, orgId, docType)
				.orElseThrow(() -> new AdempiereException("Cannot find invoice for identifier!")
						.appendParametersToMessage()
						.setParameter("Identifier", invoiceIdentifier.getRawIdentifierString()));

		return getInvoiceToAllocate(invoiceId, dateTrx);
	}

	@NonNull
	private PayableDocument buildPayableDocument(
			@NonNull final InvoiceToAllocate invoiceToAllocate,
			@NonNull final ZonedDateTime dateTrx,
			@NonNull final CurrencyId currencyId,
			@NonNull final JsonPaymentAllocationLine allocationLine)
	{
		Check.assumeNotNull(invoiceToAllocate.getInvoiceId(), "InvoiceId cannot be null at this point!");

		final ClientAndOrgId clientAndOrgId = invoiceToAllocate.getClientAndOrgId();
		final Money openAmt = moneyService.toMoney(invoiceToAllocate.getOpenAmountConverted());
		final CurrencyId invoiceCurrencyId = openAmt.getCurrencyId();
		final Money requestTotalAmt = convertToInvoiceCurrencyIfNecessary(allocationLine.getTotalAmt(),
																		  dateTrx,
																		  clientAndOrgId,
																		  currencyId,
																		  invoiceCurrencyId);

		validateRequestAmounts(allocationLine.getInvoiceIdentifier(), openAmt, requestTotalAmt);

		final Money invoiceProcessingFee = getInvoiceProcessingFee(invoiceToAllocate, dateTrx);

		final SOTrx soTrx = invoiceToAllocate.getDocBaseType().getSoTrx();

		return PayableDocument.builder()
				.invoiceId(invoiceToAllocate.getInvoiceId())
				.bpartnerId(invoiceToAllocate.getBpartnerId())
				.documentNo(invoiceToAllocate.getDocumentNo())
				.soTrx(soTrx)
				.creditMemo(invoiceToAllocate.getDocBaseType().isCreditMemo())
				.openAmt(openAmt.negateIf(soTrx.isPurchase()))
				.date(invoiceToAllocate.getDateInvoiced())
				.clientAndOrgId(clientAndOrgId)
				.currencyConversionTypeId(invoiceToAllocate.getCurrencyConversionTypeId())
				.amountsToAllocate(AllocationAmounts.builder()
										   .payAmt(getPayAmt(requestTotalAmt, invoiceProcessingFee))
										   .discountAmt(getDiscountAmt(dateTrx,
																	   clientAndOrgId,
																	   allocationLine.getDiscountAmt(),
																	   currencyId,
																	   invoiceCurrencyId))
										   .writeOffAmt(getWriteOffAmt(dateTrx,
																	   clientAndOrgId,
																	   allocationLine.getWriteOffAmt(),
																	   currencyId,
																	   invoiceCurrencyId))
										   .invoiceProcessingFee(invoiceProcessingFee)
										   .build()
										   .convertToRealAmounts(invoiceToAllocate.getMultiplier()))
				.build();
	}

	@Nullable
	private Money getInvoiceProcessingFee(
			@NonNull final InvoiceToAllocate invoiceToAllocate,
			@NonNull final ZonedDateTime dateTrx)
	{
		Check.assumeNotNull(invoiceToAllocate.getInvoiceId(), "InvoiceId cannot be null at this point!");

		return invoiceProcessingServiceCompanyService.computeFee(InvoiceProcessingFeeComputeRequest.builder()
																		 .orgId(invoiceToAllocate.getClientAndOrgId().getOrgId())
																		 .evaluationDate(dateTrx)
																		 .customerId(invoiceToAllocate.getBpartnerId())
																		 .docTypeId(invoiceToAllocate.getDocTypeId())
																		 .invoiceId(invoiceToAllocate.getInvoiceId())
																		 .invoiceGrandTotal(invoiceToAllocate.getGrandTotal())
																		 .build())
				.map(InvoiceProcessingFeeCalculation::getFeeAmountIncludingTax)
				.map(moneyService::toMoney)
				.orElse(null);
	}

	@NonNull
	private InvoiceToAllocate getInvoiceToAllocate(
			@NonNull final InvoiceId invoiceId,
			@NonNull final ZonedDateTime evaluationDate)
	{
		final InvoiceToAllocateQuery invoiceToAllocateQuery = InvoiceToAllocateQuery.builder()
				.evaluationDate(evaluationDate)
				.onlyInvoiceId(invoiceId)
				.build();

		return paymentAllocationRepository.retrieveInvoicesToAllocate(invoiceToAllocateQuery).get(0);
	}

	@NonNull
	private PaymentDocument buildPaymentDocument(
			@NonNull final PaymentId paymentId,
			@NonNull final ZonedDateTime dateTrx)
	{
		final PaymentToAllocate paymentToAllocate = getPaymentToAllocate(paymentId, dateTrx);

		final PaymentAmtMultiplier amtMultiplier = paymentToAllocate.getPaymentAmtMultiplier();

		final Money openAmt = amtMultiplier.convertToRealValue(paymentToAllocate.getOpenAmt())
				.toMoney(moneyService::getCurrencyIdByCurrencyCode);

		return PaymentDocument.builder()
				.paymentId(paymentToAllocate.getPaymentId())
				.bpartnerId(paymentToAllocate.getBpartnerId())
				.documentNo(paymentToAllocate.getDocumentNo())
				.paymentDirection(paymentToAllocate.getPaymentDirection())
				.openAmt(openAmt)
				.amountToAllocate(openAmt)
				.dateTrx(paymentToAllocate.getDateTrx())
				.clientAndOrgId(paymentToAllocate.getClientAndOrgId())
				.paymentCurrencyContext(paymentToAllocate.getPaymentCurrencyContext())
				.build();
	}

	@NonNull
	private PaymentToAllocate getPaymentToAllocate(
			@NonNull final PaymentId paymentId,
			@NonNull final ZonedDateTime dateTrx)
	{
		final PaymentToAllocateQuery query = PaymentToAllocateQuery.builder()
				.evaluationDate(dateTrx)
				.additionalPaymentIdToInclude(paymentId)
				.build();

		return paymentAllocationRepository.retrievePaymentsToAllocate(query).get(0);
	}

	@NonNull
	private Optional<InvoiceId> retrieveInvoiceId(
			@NonNull final IdentifierString invoiceIdentifier,
			@NonNull final OrgId orgId,
			@Nullable final DocBaseAndSubType docType)
	{
		final InvoiceQuery invoiceQuery = createInvoiceQuery(invoiceIdentifier, orgId, docType);
		return invoiceDAO.retrieveIdByInvoiceQuery(invoiceQuery);
	}

	private InvoiceQuery createInvoiceQuery(
			@NonNull final IdentifierString identifierString,
			@NonNull final OrgId orgId,
			@Nullable final DocBaseAndSubType docType)
	{
		final InvoiceQuery.InvoiceQueryBuilder invoiceQueryBuilder = InvoiceQuery.builder()
				.orgId(orgId)
				.docType(docType);

		switch (identifierString.getType())
		{
			case METASFRESH_ID:
				invoiceQueryBuilder.invoiceId(identifierString.asMetasfreshId().getValue());
				break;
			case EXTERNAL_ID:
				invoiceQueryBuilder.externalId(identifierString.asExternalId());
				break;
			case DOC:
				invoiceQueryBuilder.documentNo(identifierString.asDoc());
				break;
			default:
				throw new AdempiereException("Invalid identifierString: " + identifierString);
		}
		return invoiceQueryBuilder.build();
	}

	private Optional<I_AD_Archive> getLastArchive(@NonNull final InvoiceId invoiceId)
	{
		return archiveBL.getLastArchive(TableRecordReference.of(I_C_Invoice.Table_Name, invoiceId));
	}

	@NonNull
	private ManualInvoice createInvoiceWithinTrx(
			@NonNull final JsonCreateInvoiceRequest request,
			@NonNull final MasterdataProvider masterdataProvider)
	{
		final CreateInvoiceRequest createInvoiceRequest = buildCreateInvoiceRequest(request.getInvoice(),
																					masterdataProvider);

		return manualInvoiceService.createInvoice(createInvoiceRequest);
	}

	@NonNull
	private CreateInvoiceRequest buildCreateInvoiceRequest(
			@NonNull final JsonCreateInvoiceRequestItem createInvoiceRequest,
			@NonNull final MasterdataProvider masterdataProvider)
	{
		final ClientAndOrgId clientAndOrgId = getClientAndOrgId(createInvoiceRequest.getHeader().getOrgCode());
		final AcctSchema acctSchema = getAcctSchema(clientAndOrgId, createInvoiceRequest.getHeader().getAcctSchemaCode());

		final CreateInvoiceRequestHeader createHeaderRequest = buildCreateInvoiceRequestHeader(createInvoiceRequest.getHeader(), masterdataProvider);

		final ImmutableMap<String, CreateInvoiceRequestLine> externalLineId2Line = createInvoiceRequest.getLines().stream()
				.map(jsonLine -> buildCreateInvoiceRequestLine(clientAndOrgId.getOrgId(), acctSchema.getChartOfAccountsId(), jsonLine, masterdataProvider))
				.collect(ImmutableMap.toImmutableMap(
						CreateInvoiceRequestLine::getExternalLineId,
						Function.identity()));

		return CreateInvoiceRequest.builder()
				.completeIt(createInvoiceRequest.getCompleteIt())
				.acctSchema(acctSchema)
				.header(createHeaderRequest)
				.externalLineId2Line(externalLineId2Line)
				.build();
	}

	@NonNull
	private CreateInvoiceRequestLine buildCreateInvoiceRequestLine(
			@NonNull final OrgId orgId,
			@NonNull final ChartOfAccountsId accountsId,
			@NonNull final JsonCreateInvoiceLineItemRequest jsonLine,
			@NonNull final MasterdataProvider masterdataProvider)
	{
		final ProductMasterDataProvider.ProductInfo productInfo = masterdataProvider
				.getProductInfo(ExternalIdentifier.of(jsonLine.getProductIdentifier()), orgId);

		final UomId uomId = getUomId(jsonLine.getUomCode()).orElseGet(productInfo::getUomId);

		return CreateInvoiceRequestLine.builder()
				.externalLineId(jsonLine.getExternalLineId())

				.line(jsonLine.getLine())
				.lineDescription(jsonLine.getLineDescription())

				.productId(productInfo.getProductId())
				.vatCodeId(getVatCodeId(jsonLine.getTaxCode(), orgId))
				.priceEntered(jsonLine.getPriceEntered())
				.priceUomId(getUomId(jsonLine.getPriceUomCode()).orElse(null))
				.qtyToInvoice(Quantitys.create(jsonLine.getQtyToInvoice(), uomId))

				.elementValueId(getElementValueId(jsonLine.getAcctCode(), accountsId))
				.productAcctType(ProductAcctType.ofName(jsonLine.getAccountName()))
				.extendedProps(jsonLine.getExtendedProps())
				.build();
	}

	@Nullable
	private ElementValueId getElementValueId(
			@Nullable final String acctCode,
			@NonNull final ChartOfAccountsId chartOfAccountsId)
	{
		if (acctCode == null)
		{
			return null;
		}

		return elementValueService.getByAccountNo(acctCode, chartOfAccountsId)
				.map(ElementValue::getId)
				.orElseThrow(() -> new AdempiereException("No ElementValue found for the specified line.acctCode!")
						.appendParametersToMessage()
						.setParameter("line.acctCode", acctCode));
	}

	@Nullable
	private VatCodeId getVatCodeId(
			@Nullable final String vatCode,
			@NonNull final OrgId orgId)
	{
		return Optional.ofNullable(vatCode)
				.map(taxCode -> vatCodeDAO.getIdByCodeAndOrgId(taxCode, orgId))
				.orElse(null);
	}

	@NonNull
	private CreateInvoiceRequestHeader buildCreateInvoiceRequestHeader(
			@NonNull final JsonCreateInvoiceRequestItemHeader invoiceHeader,
			@NonNull final MasterdataProvider masterdataProvider)
	{
		final ClientAndOrgId clientAndOrgId = getClientAndOrgId(invoiceHeader.getOrgCode());

		final ZoneId zoneId = orgDAO.getTimeZone(clientAndOrgId.getOrgId());

		final Instant dateInvoiced = invoiceHeader.getDateInvoicedAsInstant(zoneId).orElse(SystemTime.asInstant());

		final BPartnerInfo bPartnerInfo = getBPartnerInfo(invoiceHeader, masterdataProvider, clientAndOrgId.getOrgId());

		final DocTypeId docTypeId = getDocTypeId(invoiceHeader.getInvoiceDocType(), clientAndOrgId);

		return CreateInvoiceRequestHeader.builder()
				.externalHeaderId(invoiceHeader.getExternalHeaderId())
				.orgId(clientAndOrgId.getOrgId())
				.billBPartnerLocationId(bPartnerInfo.getBPartnerLocationIdOrError())
				.billContactId(bPartnerInfo.getContactId())
				.dateInvoiced(dateInvoiced)
				.dateAcct(invoiceHeader.getDateAcctAsInstant(zoneId)
								  .orElse(dateInvoiced))
				.dateOrdered(invoiceHeader.getDateOrderedAsInstant(zoneId)
									 .orElse(dateInvoiced))
				.docTypeId(docTypeId)
				.poReference(invoiceHeader.getPoReference())
				.soTrx(SOTrx.ofNameNotNull(invoiceHeader.getSoTrx()))
				.currencyId(getCurrencyId(invoiceHeader.getCurrencyCode()))
				.grandTotal(invoiceHeader.getGrandTotal())
				.taxTotal(invoiceHeader.getTaxTotal())
				.extendedProps(invoiceHeader.getExtendedProps())
				.build();
	}

	@NonNull
	private ClientAndOrgId getClientAndOrgId(@NonNull final String orgCode)
	{
		final OrgId orgId = retrieveOrgIdOrDefault(orgCode);
		final ClientId clientId = orgDAO.getOrgInfoById(orgId).getClientId();

		return ClientAndOrgId.ofClientAndOrg(clientId, orgId);
	}

	@NonNull
	private static BPartnerInfo getBPartnerInfo(
			@NonNull final JsonCreateInvoiceRequestItemHeader invoiceHeader,
			@NonNull final MasterdataProvider masterdataProvider,
			@NonNull final OrgId orgId)
	{
		final JsonRequestBPartnerLocationAndContact jsonRequestBPartnerLocationAndContact = JsonRequestBPartnerLocationAndContact.builder()
				.bPartnerIdentifier(invoiceHeader.getBillPartnerIdentifier())
				.bPartnerLocationIdentifier(invoiceHeader.getBillLocationIdentifier())
				.contactIdentifier(invoiceHeader.getBillContactIdentifier())
				.build();

		return masterdataProvider.getBPartnerInfoNotNull(jsonRequestBPartnerLocationAndContact, orgId);
	}

	@NonNull
	private CurrencyId getCurrencyId(@NonNull final String currencyCode)
	{
		return Optional.ofNullable(currencyService.getCurrencyId(currencyCode))
				.orElseThrow(() -> new AdempiereException("Wrong currency: " + currencyCode));
	}

	@NonNull
	private AcctSchema getAcctSchema(
			@NonNull final ClientAndOrgId clientAndOrgId,
			@Nullable final String acctSchemaCode)
	{
		return Optional.ofNullable(acctSchemaCode)
				.map(schemaCode -> acctSchemaDAO.getByClientAndName(clientAndOrgId.getClientId(), schemaCode))
				.orElseGet(() -> acctSchemaDAO.getByClientAndOrg(clientAndOrgId.getClientId(), clientAndOrgId.getOrgId()));
	}

	@NonNull
	private DocTypeId getDocTypeId(
			@NonNull final JsonDocTypeInfo invoiceDocType,
			@NonNull final ClientAndOrgId clientAndOrgId)
	{
		final DocTypeId docTypeId = docTypeService.getInvoiceDocTypeId(
				DocBaseType.ofCode(invoiceDocType.getDocBaseType()),
				invoiceDocType.getDocSubType(),
				clientAndOrgId.getOrgId());

		return Optional.ofNullable(docTypeId)
				.orElseThrow(() -> new AdempiereException("No DocumentType found!")
						.appendParametersToMessage()
						.setParameter("ClientId", clientAndOrgId.getClientId())
						.setParameter("OrgId", clientAndOrgId.getOrgId())
						.setParameter("DocBaseType", invoiceDocType.getDocBaseType())
						.setParameter("DocSubType", invoiceDocType.getDocSubType()));
	}

	@NonNull
	private Optional<UomId> getUomId(@Nullable final String uomCode)
	{
		return Optional.ofNullable(uomCode)
				.map(X12DE355::ofCode)
				.map(uomDAO::getUomIdByX12DE355);
	}

	@NonNull
	private BPartnerId getBPartnerId(
			@NonNull final String bpartnerIdentifier,
			@NonNull final OrgId orgId)
	{
		return jsonRetrieverService.resolveBPartnerExternalIdentifier(ExternalIdentifier.of(bpartnerIdentifier), orgId)
				.orElseThrow(() -> MissingResourceException.builder()
						.resourceName("BPartner")
						.resourceIdentifier(bpartnerIdentifier)
						.build());
	}

	@NonNull
	private PaymentId createPayment(
			@NonNull final JsonInvoicePaymentCreateRequest request,
			@NonNull final OrgId orgId,
			@NonNull final CurrencyId currencyId,
			@NonNull final ZonedDateTime dateTrx)
	{
		final BankAccountId bankAccountId = paymentService.determineOrgBPartnerBankAccountId(orgId, currencyId, request.getTargetIBAN())
				.orElseThrow(() -> new AdempiereException(String.format(
						"Cannot find Bank Account for org-id: %s, currency: %s and iban: %s", orgId, currencyId, request.getTargetIBAN())));

		final DefaultPaymentBuilder paymentBuilder = getPaymentBuilder(request);

		final I_C_Payment paymentRecord = paymentBuilder
				.bpartnerId(getBPartnerId(request.getBpartnerIdentifier(), orgId))
				.payAmt(request.getAmount())
				.discountAmt(request.getDiscountAmt())
				.writeoffAmt(request.getWriteOffAmt())
				.currencyId(currencyId)
				.orgBankAccountId(bankAccountId)
				.adOrgId(orgId)
				.tenderType(TenderType.DirectDeposit)
				.dateAcct(dateTrx.toLocalDate())
				.dateTrx(dateTrx.toLocalDate())
				.externalId(ExternalId.ofOrNull(request.getExternalPaymentId()))
				.isAutoAllocateAvailableAmt(true)
				.createAndProcess();

		return PaymentId.ofRepoId(paymentRecord.getC_Payment_ID());
	}

	@NonNull
	private DefaultPaymentBuilder getPaymentBuilder(@NonNull final JsonInvoicePaymentCreateRequest request)
	{
		return Optional.ofNullable(request.getType())
				.filter(type -> JsonPaymentDirection.INBOUND != type)
				.map(type -> paymentService.newOutboundPaymentBuilder())
				.orElseGet(paymentService::newInboundReceiptBuilder);
	}

	@NonNull
	private ZonedDateTime getDateTrx(
			@NonNull final JsonInvoicePaymentCreateRequest request,
			@NonNull final OrgId orgId)
	{
		final ZoneId zoneId = orgDAO.getTimeZone(orgId);
		return CoalesceUtil.coalesceNotNull(request.getTransactionDate(), SystemTime.asLocalDate()).atStartOfDay(zoneId);
	}

	@Nullable
	private Money getWriteOffAmt(
			@NonNull final ZonedDateTime dateTrx,
			@NonNull final ClientAndOrgId clientAndOrgId,
			@Nullable final BigDecimal writeOffValue,
			@NonNull final CurrencyId requestCurrencyId,
			@NonNull final CurrencyId invoiceCurrencyId)
	{
		return Optional.ofNullable(writeOffValue)
				.map(writeOff -> convertToInvoiceCurrencyIfNecessary(writeOff,
																	 dateTrx,
																	 clientAndOrgId,
																	 requestCurrencyId,
																	 invoiceCurrencyId))
				.orElse(null);
	}

	@Nullable
	private Money getDiscountAmt(
			@NonNull final ZonedDateTime dateTrx,
			@NonNull final ClientAndOrgId clientAndOrgId,
			@Nullable final BigDecimal discountAmtValue,
			@NonNull final CurrencyId requestCurrencyId,
			@NonNull final CurrencyId invoiceCurrencyId)
	{
		return Optional.ofNullable(discountAmtValue)
				.map(discount -> convertToInvoiceCurrencyIfNecessary(discount,
																	 dateTrx,
																	 clientAndOrgId,
																	 requestCurrencyId,
																	 invoiceCurrencyId))
				.orElse(null);
	}

	@NonNull
	private Money convertToInvoiceCurrencyIfNecessary(
			@NonNull final BigDecimal amt,
			@NonNull final ZonedDateTime dateTrx,
			@NonNull final ClientAndOrgId clientAndOrgId,
			@NonNull final CurrencyId requestCurrencyId,
			@NonNull final CurrencyId invoiceCurrencyId)
	{
		if (!invoiceCurrencyId.equals(requestCurrencyId))
		{
			final CurrencyConversionContext currencyConversionContext = currencyBL.createCurrencyConversionContext(
					dateTrx.toInstant(),
					ConversionTypeMethod.Spot,
					clientAndOrgId.getClientId(),
					clientAndOrgId.getOrgId());

			return currencyBL.convert(currencyConversionContext,
									  amt,
									  requestCurrencyId,
									  invoiceCurrencyId)
					.getAmountAsMoney();
		}
		else
		{
			return Money.of(amt, invoiceCurrencyId);
		}
	}

	private static void validateRequestAmounts(
			@NonNull final String invoiceIdentifier,
			@NonNull final Money openAmt,
			@NonNull final Money requestTotalAmt)
	{
		if (requestTotalAmt.isGreaterThan(openAmt))
		{
			throw new AdempiereException("Line's total amount cannot be greater than invoice's open amount!")
					.appendParametersToMessage()
					.setParameter("InvoiceIdentifier", invoiceIdentifier)
					.setParameter("Invoice.OpenAmt", openAmt.toBigDecimal())
					.setParameter("Line.TotalAmt", requestTotalAmt.toBigDecimal());
		}
	}
	
	private static void validateInvoice(
			@NonNull final InvoiceToAllocate invoiceToAllocate,
			@NonNull final IdentifierString invoiceIdentifier,
			@Nullable final JsonPaymentDirection type)
	{
		final boolean requestSoTrx = type == null || JsonPaymentDirection.INBOUND == type;
		final boolean invoiceSoTrx = invoiceToAllocate.getDocBaseType().getSoTrx().isSales();
		if (invoiceSoTrx != requestSoTrx)
		{
			throw new AdempiereException("The referenced invoice does not match the payment type!")
					.appendParametersToMessage()
					.setParameter("Identifier", invoiceIdentifier.getRawIdentifierString())
					.setParameter("Type", type)
					.setParameter("Invoice.SOTrx", invoiceSoTrx)
					.setParameter("Invoice.C_Invoice_ID", invoiceToAllocate.getInvoiceId());
		}
	}

	@Nullable
	private static DocBaseAndSubType getDocType(@NonNull final JsonPaymentAllocationLine line)
	{
		return Optional.ofNullable(line.getDocBaseType())
				.filter(Check::isNotBlank)
				.map(baseType -> DocBaseAndSubType.of(baseType, line.getDocSubType()))
				.orElse(null);
	}

	private static void validateAllocationLineAmounts(@Nullable final List<JsonPaymentAllocationLine> lines)
	{
		final boolean amountsAreMissing = lines != null && lines
				.stream()
				.anyMatch(line -> !line.isAtLeastOneAmtSet());

		if (amountsAreMissing)
		{
			throw new AdempiereException("At least one of the following allocation amounts are mandatory in every line: amount, discountAmt, writeOffAmt");
		}
	}

	@NonNull
	private static OrgId getOrgId(@Nullable final String orgCode)
	{
		return Optional.ofNullable(RestUtils.retrieveOrgIdOrDefault(orgCode))
				.filter(OrgId::isRegular)
				.orElseThrow(() -> new AdempiereException("Cannot find the orgId from either orgCode=" + orgCode + " or the current user's context."));
	}

	@NonNull
	private static Money getPayAmt(
			@NonNull final Money requestTotalAmt,
			@Nullable final Money invoiceProcessingFee)
	{
		return Optional.ofNullable(invoiceProcessingFee)
				.map(requestTotalAmt::subtract)
				.orElse(requestTotalAmt);
	}
}
