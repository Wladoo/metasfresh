package de.metas.payment.paymentterm;

import java.util.Optional;

import de.metas.payment.paymentterm.impl.PaymentTerm;
import lombok.NonNull;
import org.compiere.model.I_C_PaymentTerm;

import de.metas.payment.paymentterm.impl.PaymentTermQuery;
import de.metas.util.ISingletonService;
import de.metas.util.lang.Percent;

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
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-2.0.html>.
 * #L%
 */

public interface IPaymentTermRepository extends ISingletonService
{
	Percent getPaymentTermDiscount(PaymentTermId paymentTermId);

	PaymentTermId getDefaultPaymentTermIdOrNull();

	PaymentTerm getById(PaymentTermId paymentTermId);

	Optional<PaymentTermId> retrievePaymentTermId(PaymentTermQuery build);

	boolean isAllowOverrideDueDate(@NonNull PaymentTermId paymentTermId);

	@Deprecated
	I_C_PaymentTerm getRecordById(@NonNull final PaymentTermId paymentTermId);
}
