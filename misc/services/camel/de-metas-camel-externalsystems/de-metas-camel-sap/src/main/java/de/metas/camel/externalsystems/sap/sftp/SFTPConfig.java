/*
 * #%L
 * de-metas-camel-sap
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

package de.metas.camel.externalsystems.sap.sftp;

import de.metas.common.util.CoalesceUtil;
import lombok.Builder;
import lombok.NonNull;
import lombok.Value;

import javax.annotation.Nullable;

@Value
@Builder
public class SFTPConfig
{
	@NonNull
	String username;

	@NonNull
	String password;

	@NonNull
	String port;

	@NonNull
	String hostName;

	@Nullable
	String targetDirectory;

	@NonNull
	String processedFilesFolder;

	@NonNull
	String erroredFilesFolder;

	@NonNull
	String pollingFrequency;

	public String getSFTPConnectionString()
	{
		//FIXME: move; moveFailed; delay should be injected from properties
		final String endpointTemplate = "%s@%s:%s/%s?password=%s&move=%s&moveFailed=%s&delay=%s";

		return String.format(endpointTemplate,
							 this.getUsername(),
							 this.getHostName(),
							 this.getPort(),
							 CoalesceUtil.coalesce(this.getTargetDirectory(), ""),
							 this.getPassword(),
							 this.getProcessedFilesFolder(),
							 this.getErroredFilesFolder(),
							 this.getPollingFrequency());
	}
}
