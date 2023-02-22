package de.metas.payment.paymentterm.impl;

import com.google.common.collect.ImmutableList;
import de.metas.acct.model.I_C_VAT_Code;
import de.metas.cache.annotation.CacheCtx;
import de.metas.forex.ForexContractRepository;
import de.metas.organization.OrgId;
import de.metas.payment.paymentterm.IPaymentTermRepository;
import de.metas.payment.paymentterm.PaymentTerm;
import de.metas.payment.paymentterm.PaymentTermId;
import de.metas.util.Check;
import de.metas.util.Services;
import de.metas.util.lang.Percent;
import lombok.NonNull;
import org.adempiere.ad.dao.IQueryBL;
import org.adempiere.ad.dao.IQueryBuilder;
import org.adempiere.ad.dao.IQueryOrderBy;
import org.adempiere.ad.trx.api.ITrx;
import org.adempiere.exceptions.AdempiereException;
import org.adempiere.exceptions.DBMoreThanOneRecordsFoundException;
import org.adempiere.model.InterfaceWrapperHelper;
import org.adempiere.util.proxy.Cached;
import org.compiere.model.I_C_PaymentTerm;
import org.compiere.util.Env;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Optional;
import java.util.Properties;

import static de.metas.util.Check.isEmpty;
import static org.adempiere.model.InterfaceWrapperHelper.loadOutOfTrx;

/*
 * #%L
 * de.metas.business
 * %%
 * Copyright (C) 2018 metas GmbH
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

public class PaymentTermRepository
{

	private final IQueryBL queryBL = Services.get(IQueryBL.class);

	public static PaymentTerm fromRecord(final I_C_PaymentTerm record)
	{
		try
		{
			return PaymentTerm.builder()
					.id(extractId(record))
					.orgId(OrgId.ofRepoId(record.getAD_Org_ID()))
					.discount(record.getDiscount())
					.discount2(record.getDiscount2())
					.discountDays(record.getDiscountDays())
					.discountDays2(record.getDiscountDays2())
					.graceDays(record.getGraceDays())
					.netDays(record.getNetDays())
					.netDay(record.getNetDay())
					._default(record.isDefault())
					.allowOverrideDueDate(record.isAllowOverrideDueDate())
					.valid(record.isValid())
					.build();
		}
		catch (final Exception ex)
		{
			throw AdempiereException.wrapIfNeeded(ex)
					.setParameter("ID", record.getC_PaymentTerm_ID())
					.appendParametersToMessage();
		}
	}

	@NonNull
	private static PaymentTermId extractId(final I_C_PaymentTerm record)
	{
		return PaymentTermId.ofRepoId(record.getC_PaymentTerm_ID());
	}

	public PaymentTerm getById(@NonNull PaymentTermId id)
	{
		final I_C_PaymentTerm record = getRecordById(id);
		return fromRecord(record);
	}

	private I_C_PaymentTerm getRecordById(final @NonNull PaymentTermId id)
	{
		return InterfaceWrapperHelper.load(id, I_C_PaymentTerm.class);
	}


	public Percent getPaymentTermDiscount(@Nullable final PaymentTermId paymentTermId)
	{
		if (paymentTermId == null)
		{
			return Percent.ZERO;
		}

		final PaymentTerm paymentTerm = getById(paymentTermId);
		if (paymentTerm == null)
		{
			return Percent.ZERO;
		}

		return Percent.of(paymentTerm.getDiscount());
	}

	public PaymentTermId getDefaultPaymentTermIdOrNull()
	{
		final int contextPaymentTerm = Env.getContextAsInt(Env.getCtx(), "#C_PaymentTerm_ID");
		if (contextPaymentTerm > 0)
		{
			return PaymentTermId.ofRepoId(contextPaymentTerm);
		}

		final int dbPaymentTermId = Services.get(IQueryBL.class)
				.createQueryBuilder(I_C_PaymentTerm.class)
				.addOnlyActiveRecordsFilter()
				.addEqualsFilter(I_C_PaymentTerm.COLUMNNAME_IsDefault, true)
				.addOnlyContextClient(Env.getCtx())
				.create()
				.firstId();
		if (dbPaymentTermId > 0)
		{
			return PaymentTermId.ofRepoId(dbPaymentTermId);
		}

		return null;
	}

	public Optional<PaymentTermId> retrievePaymentTermId(@NonNull final PaymentTermQuery query)
	{
		final IQueryBuilder<I_C_PaymentTerm> queryBuilder = queryBL
				.createQueryBuilder(I_C_PaymentTerm.class)
				.addOnlyActiveRecordsFilter();

		Check.assumeNotNull(query.getOrgId(), "Org Id is missing from PaymentTermQuery ", query);

		queryBuilder.addInArrayFilter(I_C_PaymentTerm.COLUMNNAME_AD_Org_ID, query.getOrgId(), OrgId.ANY);

		if (query.getExternalId() != null)
		{
			queryBuilder.addEqualsFilter(I_C_PaymentTerm.COLUMNNAME_ExternalId, query.getExternalId().getValue());
		}

		if (!isEmpty(query.getValue(), true))
		{
			queryBuilder.addEqualsFilter(I_C_PaymentTerm.COLUMNNAME_Value, query.getValue());
		}

		try
		{
			final PaymentTermId firstId = queryBuilder
					.create()
					.firstIdOnly(PaymentTermId::ofRepoIdOrNull);
			return Optional.ofNullable(firstId);
		}
		catch (final DBMoreThanOneRecordsFoundException e)
		{
			// augment and rethrow
			throw e.appendParametersToMessage().setParameter("paymentTermQuery", query);
		}
	}

	public boolean isAllowOverrideDueDate(@NonNull final PaymentTermId paymentTermId)
	{
		return Services.get(IQueryBL.class)
				.createQueryBuilder(I_C_PaymentTerm.class)
				.addEqualsFilter(I_C_PaymentTerm.COLUMNNAME_C_PaymentTerm_ID, paymentTermId)
				.addEqualsFilter(I_C_PaymentTerm.COLUMNNAME_IsAllowOverrideDueDate, true)
				.create()
				.anyMatch();
	}

	/**
	 * Retrieves all active payment terms
	 */
	@Cached(cacheName = I_C_PaymentTerm.Table_Name+ "#All")
	public List<PaymentTerm> retrievePaymentTerm()
	{
		return queryBL
				.createQueryBuilder(I_C_PaymentTerm.class)
				.addOnlyActiveRecordsFilter()
				//
				.orderBy()
				.addColumn(I_C_PaymentTerm.COLUMNNAME_IsDefault)
				.addColumn(I_C_PaymentTerm.COLUMNNAME_IsAllowOverrideDueDate)
				.endOrderBy()
				//
				.create()
				.stream()
				.map(PaymentTermRepository::fromRecord)
				.collect(ImmutableList.toImmutableList());
	}

}