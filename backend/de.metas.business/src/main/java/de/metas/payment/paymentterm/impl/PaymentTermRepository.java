package de.metas.payment.paymentterm.impl;

import de.metas.cache.CCache;
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
import org.adempiere.exceptions.AdempiereException;
import org.adempiere.exceptions.DBMoreThanOneRecordsFoundException;
import org.adempiere.model.InterfaceWrapperHelper;
import org.adempiere.service.ClientId;
import org.compiere.model.IQuery;
import org.compiere.model.I_C_PaymentTerm;
import org.compiere.util.Env;

import javax.annotation.Nullable;
import java.util.Optional;

import static de.metas.util.Check.isEmpty;

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

public class PaymentTermRepository implements IPaymentTermRepository
{

	private final IQueryBL queryBL = Services.get(IQueryBL.class);
	private final CCache<PaymentTermId, PaymentTerm> paymentTermCCacheById = CCache.newCache(I_C_PaymentTerm.Table_Name, 50, CCache.EXPIREMINUTES_Never);


	@NonNull
	private static PaymentTermId extractId(final I_C_PaymentTerm record)
	{
		return PaymentTermId.ofRepoId(record.getC_PaymentTerm_ID());
	}

	@Override
	public PaymentTerm getById(@NonNull PaymentTermId id)
	{
		return paymentTermCCacheById.getOrLoad(id, () -> {
			final I_C_PaymentTerm record = getRecordById(id);
			return fromRecord(record);
		});
	}

	@Override
	public I_C_PaymentTerm getRecordById(final @NonNull PaymentTermId id)
	{
		return InterfaceWrapperHelper.load(id, I_C_PaymentTerm.class);
	}


	public static PaymentTerm fromRecord(final I_C_PaymentTerm record)
	{
		try
		{
			return PaymentTerm.builder()
					.id(extractId(record))
					.orgId(OrgId.ofRepoId(record.getAD_Org_ID()))
					.clientId(ClientId.ofRepoId(record.getAD_Client_ID()))
					.value(record.getValue())
					.name(record.getName())
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

	@Override
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

	@Override
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
		try
		{
			final PaymentTermId firstId = toSqlQuery(query)
					.firstIdOnly(PaymentTermId::ofRepoIdOrNull);
			return Optional.ofNullable(firstId);
		}
		catch (final DBMoreThanOneRecordsFoundException e)
		{
			// augment and rethrow
			throw e.appendParametersToMessage().setParameter("paymentTermQuery", query);
		}
	}

	private IQuery<I_C_PaymentTerm> toSqlQuery(@NonNull PaymentTermQuery query)
	{
		final IQueryBuilder<I_C_PaymentTerm> queryBuilder = queryBL.createQueryBuilder(I_C_PaymentTerm.class)
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

		return queryBuilder.create();
	}

	public boolean isAllowOverrideDueDate(@NonNull final PaymentTermId paymentTermId)
	{
		final PaymentTerm paymentTerm = getById(paymentTermId);

		return paymentTerm.isAllowOverrideDueDate();
	}

}