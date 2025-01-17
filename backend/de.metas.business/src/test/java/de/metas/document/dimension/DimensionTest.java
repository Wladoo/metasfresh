package de.metas.document.dimension;

import de.metas.order.OrderId;
import de.metas.product.ProductId;
import de.metas.product.acct.api.ActivityId;
import de.metas.project.ProjectId;
import de.metas.sectionCode.SectionCodeId;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

public class DimensionTest
{
	public static Dimension newFullyPopulatedDimension()
	{
		return Dimension.builder()
				.projectId(ProjectId.ofRepoId(1))
				.campaignId(2)
				.activityId(ActivityId.ofRepoId(3))
				.salesOrderId(OrderId.ofRepoId(4))
				.sectionCodeId(SectionCodeId.ofRepoId(5))
				.productId(ProductId.ofRepoId(6))
				.user1_ID(7)
				.user2_ID(8)
				.userElement1Id(9)
				.userElement2Id(10)
				.userElementString1("userElementString1")
				.userElementString2("userElementString2")
				.userElementString3("userElementString3")
				.userElementString4("userElementString4")
				.userElementString5("userElementString5")
				.userElementString6("userElementString6")
				.userElementString7("userElementString7")
				.build();
	}

	@Nested
	class fallbackTo
	{
		@Test
		void empty_fallbackTo_fullyPopulated()
		{
			Dimension empty = Dimension.builder().build();
			final Dimension fullyPopulated = newFullyPopulatedDimension();
			Assertions.assertThat(empty.fallbackTo(fullyPopulated)).isSameAs(fullyPopulated);
		}

		@Test
		void fullyPopulated_fallbackTo_empty()
		{
			Dimension empty = Dimension.builder().build();
			final Dimension fullyPopulated = newFullyPopulatedDimension();
			Assertions.assertThat(fullyPopulated.fallbackTo(empty)).isSameAs(fullyPopulated);
		}

		@Test
		void partiallyPopulated_fallbackTo_partiallyPopulated()
		{
			final Dimension dim1 = Dimension.builder().productId(ProductId.ofRepoId(1)).build();
			final Dimension dim2 = Dimension.builder().sectionCodeId(SectionCodeId.ofRepoId(2)).build();

			Assertions.assertThat(dim1.fallbackTo(dim2))
					.isEqualTo(Dimension.builder()
							.productId(ProductId.ofRepoId(1))
							.sectionCodeId(SectionCodeId.ofRepoId(2))
							.build());
		}

	}
}