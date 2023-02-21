/*
 * #%L
 * de.metas.business
 * %%
 * Copyright (C) 2023 metas GmbH
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

package de.metas.payment.paymentterm;

import de.metas.organization.OrgId;
import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;

import javax.annotation.Nullable;
import java.math.BigDecimal;

public class PaymentTerm
{
	@Getter @NonNull private final PaymentTermId id;
	@Getter @NonNull private final OrgId orgId;
	@Getter @NonNull private final BigDecimal discount;
	@Getter @NonNull private final BigDecimal discount2;

	@Getter private final int discountDays;
	@Getter private final int discountDays2;
	@Getter private final int graceDays;
	@Getter private final int netDays;

	@Getter @Nullable private final String netDay;
	@Nullable final Boolean _default;
	@Nullable final Boolean allowOverrideDueDate;
	@Nullable final  Boolean valid;

	@Builder
	private PaymentTerm(
			final PaymentTermId id,
			final OrgId orgId,
			final BigDecimal discount,
			final BigDecimal discount2,
			final int discountDays,
			final int discountDays2,
			final int graceDays,
			final String netDay,
			final int netDays,
			final Boolean _default,
			final Boolean allowOverrideDueDate,
			final Boolean valid)
	{

		this.id = id;
		this.discount = discount;
		this.discount2 = discount2;
		this.discountDays = discountDays;
		this.orgId = orgId;
		this.discountDays2 = discountDays2;
		this.graceDays = graceDays;
		this.netDay = netDay;
		this.netDays = netDays;
		this._default = _default;
		this.allowOverrideDueDate = allowOverrideDueDate;
		this.valid = valid;
	}
}
