package org.reactome.release;

import org.junit.jupiter.api.*;

import java.util.*;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.hamcrest.collection.IsIterableContainingInOrder.contains;

public class NCBIEntryTest {

	private final String[] uniprotAccessions = { "Q12345", "P23456" };
	private final String eventName = "test event";
	private final String eventStId = "R-HSA-54321";
	private NCBIEntry entry1;
	private NCBIEntry entry2;
	private ReactomeEvent event;

	@BeforeEach
	public void createFixtures() {
		entry1 = new NCBIEntry(
			1L,
			uniprotAccessions[0],
			"UniProt:" + uniprotAccessions[0],
			new HashSet<>(Arrays.asList("1", "2"))
		);
		entry2 = new NCBIEntry(
			2L,
			uniprotAccessions[1],
			"UniProt:" + uniprotAccessions[1],
			new HashSet<>(Arrays.asList("3", "4")));
		event = new ReactomeEvent(1L, eventName, eventStId);

	}

	@Test
	public void sortsByUniProtAccessionAscendingly() {
		List<NCBIEntry> ncbiEntries = new ArrayList<>();
		ncbiEntries.add(entry1);
		ncbiEntries.add(entry2);

		Collections.sort(ncbiEntries);

		assertThat(ncbiEntries, contains(entry2, entry1));
	}

	@Test
	public void getEventLinkXML() {
		final String NCBI_GENE_ID = "12345";

		String eventLinkXML = entry1.getEventLinkXML(NCBI_GENE_ID, event);

		assertThat(eventLinkXML, containsString("<ObjId>" + NCBI_GENE_ID + "</ObjId>"));
		assertThat(eventLinkXML, containsString("<Base>&event.base.url;</Base>"));
		assertThat(eventLinkXML, containsString("<Rule>" + eventStId + "</Rule>"));
		assertThat(eventLinkXML, containsString("<UrlName>Reactome Event:" + eventName));
	}

	@Test
	public void getEntityLinkXML() {
		final String NCBI_GENE_ID = "12345";

		String entityLinkXML = entry1.getEntityLinkXML(NCBI_GENE_ID);

		assertThat(entityLinkXML, containsString("<ObjId>" + NCBI_GENE_ID + "</ObjId>"));
		assertThat(entityLinkXML, containsString("<Base>&entity.base.url;</Base>"));
		assertThat(entityLinkXML, containsString("<Rule>" + uniprotAccessions[0] + "</Rule>"));
		assertThat(entityLinkXML, containsString("<UrlName>Reactome Entity:" + uniprotAccessions[0]));
	}
}