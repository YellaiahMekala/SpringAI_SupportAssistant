package com.springai.knowledge;

import com.springai.TestPostgresConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static com.springai.testsupport.TestFixtures.AGENT;
import static com.springai.testsupport.TestFixtures.ALICE;
import static com.springai.testsupport.TestFixtures.token;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The document endpoints: restricted to support agents, and strict about what an upload may be. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestPostgresConfig.class)
@ActiveProfiles("demo")
@Testcontainers(disabledWithoutDocker = true)
class DocumentApiTest {

    private static final String PROBLEM_JSON = "application/problem+json";

    @Autowired
    private MockMvc mvc;

    private static MockMultipartFile file(String name, String text) {
        return new MockMultipartFile("file", name, "text/markdown", text.getBytes(StandardCharsets.UTF_8));
    }

    private static String unique() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    // ---- who may use it ----

    @Test
    void noTokenIs401() throws Exception {
        mvc.perform(multipart("/api/v1/documents").file(file("a.md", "text")))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/documents")).andExpect(status().isUnauthorized());
    }

    @Test
    void aCustomerCannotUploadOrListDocuments() throws Exception {
        mvc.perform(multipart("/api/v1/documents").file(file("a.md", "text " + unique())).with(token(ALICE, "customer")))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        mvc.perform(get("/api/v1/documents").with(token(ALICE, "customer")))
                .andExpect(status().isForbidden());
    }

    // ---- a good upload ----

    @Test
    void anAgentUploadsAMarkdownFileAndItIsStoredChunkedAndEmbedded() throws Exception {
        String marker = unique();
        mvc.perform(multipart("/api/v1/documents")
                        .file(file("gift-wrap-policy.md", "# Gift wrap " + marker + "\n\nGift wrap costs 3 units."))
                        .with(token(AGENT, "support_agent")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("CREATED"))
                .andExpect(jsonPath("$.title").value("gift wrap policy"))   // suggested from the file name
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.chunks").value(1));
    }

    @Test
    void uploadingTheSameFileAgainIsReportedAsADuplicate() throws Exception {
        MockMultipartFile same = file("same.md", "Same content " + unique());
        mvc.perform(multipart("/api/v1/documents").file(same).with(token(AGENT, "support_agent")))
                .andExpect(jsonPath("$.status").value("CREATED"));

        mvc.perform(multipart("/api/v1/documents").file(same).with(token(AGENT, "support_agent")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DUPLICATE"));
    }

    @Test
    void anExplicitTitleOverridesTheFileName() throws Exception {
        mvc.perform(multipart("/api/v1/documents").file(file("anything.md", "Body " + unique()))
                        .param("title", "Chosen title").with(token(AGENT, "support_agent")))
                .andExpect(jsonPath("$.title").value("Chosen title"));
    }

    @Test
    void theListShowsDocumentsWithTheirChunkAndEmbeddingCounts() throws Exception {
        String marker = unique();
        mvc.perform(multipart("/api/v1/documents").file(file("listed-" + marker + ".md", "Listed " + marker))
                .with(token(AGENT, "support_agent"))).andExpect(status().isCreated());

        mvc.perform(get("/api/v1/documents").with(token(AGENT, "support_agent")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.title == 'listed " + marker + "')].chunks").value(1))
                .andExpect(jsonPath("$[?(@.title == 'listed " + marker + "')].embeddedForProvider").value(1))
                .andExpect(jsonPath("$[?(@.title == 'listed " + marker + "')].visibility").value("PUBLIC"));
    }

    @Test
    void anInternalDocumentCanBeUploadedWithAgentOnlyVisibility() throws Exception {
        mvc.perform(multipart("/api/v1/documents").file(file("internal.md", "Internal " + unique()))
                        .param("visibility", "AGENT_ONLY").with(token(AGENT, "support_agent")))
                .andExpect(status().isCreated());
    }

    // ---- what an upload may not be ----

    @Test
    void onlyMarkdownAndTextFilesAreAccepted() throws Exception {
        for (String name : new String[] {"report.pdf", "tool.exe", "archive.zip", "noextension", "page.html"}) {
            mvc.perform(multipart("/api/v1/documents").file(file(name, "text " + unique()))
                            .with(token(AGENT, "support_agent")))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
                    .andExpect(jsonPath("$.code").value("INVALID_DOCUMENT"));
        }
    }

    @Test
    void aBinaryFileWithATextExtensionIsRefused() throws Exception {
        MockMultipartFile binary = new MockMultipartFile("file", "image.md", "text/markdown",
                new byte[] {(byte) 0x89, 'P', 'N', 'G', 0x00, 0x01, 0x02});

        mvc.perform(multipart("/api/v1/documents").file(binary).with(token(AGENT, "support_agent")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_DOCUMENT"));
    }

    @Test
    void anOversizedFileIsRefusedWithoutEchoingItsContent() throws Exception {
        MockMultipartFile big = new MockMultipartFile("file", "big.txt", "text/plain", new byte[1_048_577]);

        mvc.perform(multipart("/api/v1/documents").file(big).with(token(AGENT, "support_agent")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_DOCUMENT"));
    }

    @Test
    void aFileNameThatLooksLikeAPathNeverBecomesOne() throws Exception {
        mvc.perform(multipart("/api/v1/documents").file(file("../../etc/evil-notes.md", "Harmless text " + unique()))
                        .with(token(AGENT, "support_agent")))
                .andExpect(status().isCreated())
                // Only the last segment is used, as a suggested title. The file itself is never written to disk.
                .andExpect(jsonPath("$.title").value("evil notes"));
    }

    @Test
    void aDocumentWithACredentialIsRefusedAs422AndTheValueIsNotEchoed() throws Exception {
        mvc.perform(multipart("/api/v1/documents")
                        .file(file("notes.md", "Portal notes " + unique() + "\n\nThe password is hunter22 for admin."))
                        .with(token(AGENT, "support_agent")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("SENSITIVE_DATA"))
                .andExpect(jsonPath("$.categories[0]").value("PASSWORD"))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("hunter22"))));
    }

    @Test
    void anUnknownVisibilityIsABadRequest() throws Exception {
        mvc.perform(multipart("/api/v1/documents").file(file("a.md", "text " + unique()))
                        .param("visibility", "SECRET").with(token(AGENT, "support_agent")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void aMissingFileIsABadRequest() throws Exception {
        mvc.perform(multipart("/api/v1/documents").with(token(AGENT, "support_agent")))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON));
    }
}
