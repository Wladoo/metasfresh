/*
 * #%L
 * de.metas.adempiere.adempiere.base
 * %%
 * Copyright (C) 2022 metas GmbH
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

package de.metas.sectionCode;

import de.metas.organization.OrgId;
import de.metas.util.Services;
import lombok.NonNull;
import org.adempiere.ad.dao.IQueryBL;
import org.compiere.model.I_M_SectionCode;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class SectionCodeRepository
{
	private final IQueryBL queryBL = Services.get(IQueryBL.class);

	@NonNull
	public Optional<SectionCodeId> getSectionCodeIdByValue(@NonNull final OrgId orgId, @NonNull final String value)
	{
		return queryBL.createQueryBuilder(I_M_SectionCode.class)
				.addOnlyActiveRecordsFilter()
				.addEqualsFilter(I_M_SectionCode.COLUMNNAME_AD_Org_ID, orgId)
				.addEqualsFilter(I_M_SectionCode.COLUMNNAME_Value, value)
				.create()
				.firstOnlyOptional(I_M_SectionCode.class)
				.map(I_M_SectionCode::getM_SectionCode_ID)
				.map(SectionCodeId::ofRepoId);
	}
}
