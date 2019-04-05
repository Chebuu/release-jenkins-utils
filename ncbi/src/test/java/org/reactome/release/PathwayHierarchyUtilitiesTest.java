package org.reactome.release;

import org.junit.jupiter.api.*;

import java.util.*;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class PathwayHierarchyUtilitiesTest {


	@Test
	public void findTopLevelPathwayIdsWithGrandParentPathways() {
		final long PATHWAY_ID = 1L;
		Map<Long, Set<Long>> pathwayHierarchy = new HashMap<>();
		pathwayHierarchy.put(1L, new HashSet<>(Arrays.asList(2L, 3L)));
		pathwayHierarchy.put(2L, new HashSet<>(Arrays.asList(4L, 5L)));

		Set<Long> topLevelPathwayIds = PathwayHierarchyUtilities.findTopLevelPathwayIds(PATHWAY_ID, pathwayHierarchy);

		// The top level pathways for id of "1" should be "3" (parent pathway) and
		// "4" and "5" (grandparent pathways through intermediate parent pathway "2")
		assertThat(topLevelPathwayIds, is(new HashSet<>(Arrays.asList(3L, 4L, 5L))));
	}

	@Test
	public void findTopLevelPathwayIdsWithNoParents() {
		final long PATHWAY_ID = 1L;
		Map<Long, Set<Long>> pathwayHierarchy = new HashMap<>();
		pathwayHierarchy.put(PATHWAY_ID, new HashSet<>());

		Set<Long> topLevelPathwayIds = PathwayHierarchyUtilities.findTopLevelPathwayIds(PATHWAY_ID, pathwayHierarchy);

		// If the pathway has no parents in the pathway hierarchy, it is a top level pathway
		assertThat(topLevelPathwayIds, is(new HashSet<>(Collections.singletonList(PATHWAY_ID))));
	}

	@Test
	public void findTopLevelPathwayIdsWithEmptyPathwayHierarchy() throws IllegalStateException {
		final long PATHWAY_ID = 1L;
		Map<Long, Set<Long>> pathwayHierarchy = new HashMap<>();

		IllegalStateException thrown = assertThrows(
			IllegalStateException.class,
			() -> PathwayHierarchyUtilities.findTopLevelPathwayIds(PATHWAY_ID, pathwayHierarchy),
			"Expected call to 'findTopLevelPathwayIds' to throw due to empty pathway hierarchy, but it didn't"
		);

		assertThat(thrown.getMessage(), containsString("Hierarchy has no values"));
	}

	@Test
	public void emptyRLEIdToPathwayIdMapFromEmptyDatabase() {
		DummyGraphDBServer dummyGraphDBServer = DummyGraphDBServer.getInstance();
	@Test
	public void retrievesTopLevelPathwayIdsFromDummyGraphDatabase() {
		final long TOP_LEVEL_PATHWAY_ID = 1640170L;

		DummyGraphDBServer dummyGraphDBServer = DummyGraphDBServer.getInstance();
		dummyGraphDBServer.initializeNeo4j();
		dummyGraphDBServer.populateDummyGraphDB();

		Set<Long> topLevelPathwayIds =
			PathwayHierarchyUtilities.getTopLevelPathwayIds(dummyGraphDBServer.getSession());

		assertThat(topLevelPathwayIds, contains(Collections.singletonList(TOP_LEVEL_PATHWAY_ID)));
	}
}