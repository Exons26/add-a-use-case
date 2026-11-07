package team.projectpulse.security;

import jakarta.persistence.EntityManager;
import org.json.JSONObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import team.projectpulse.AbstractIntegrationTest;
import team.projectpulse.system.StatusCode;
import team.projectpulse.user.PeerEvaluationUser;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * FR-SEC-active-account: a token issued before an account was deactivated stops working on the very next request,
 * for students (UC-STU-deactivate-student extension 6a) and instructors alike.
 */
@DisplayName("Integration tests for the per-request active-account check")
class ActiveAccountIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    EntityManager entityManager;

    @Value("${api.endpoint.base-url}")
    String baseUrl;


    private String login(String email) throws Exception {
        String body = this.mockMvc.perform(post(this.baseUrl + "/users/login").with(httpBasic(email, "123456")))
                .andReturn().getResponse().getContentAsString();
        return "Bearer " + new JSONObject(body).getJSONObject("data").getString("token");
    }

    private PeerEvaluationUser user(String email) {
        return this.entityManager.createQuery("select u from PeerEvaluationUser u where u.email = :email", PeerEvaluationUser.class)
                .setParameter("email", email).getSingleResult();
    }

    private void disable(PeerEvaluationUser user) {
        user.setEnabled(false);
        this.entityManager.flush();
    }

    @Test
    void testActiveStudentTokenStillWorks() throws Exception {
        String johnToken = login("j.smith@abc.edu");
        Integer johnId = user("j.smith@abc.edu").getId();

        this.mockMvc.perform(get(this.baseUrl + "/students/" + johnId).header(HttpHeaders.AUTHORIZATION, johnToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.flag").value(true));
    }

    @Test
    void testDeactivatedStudentsStillValidTokenIsRefusedOnTheNextRequest() throws Exception {
        String johnToken = login("j.smith@abc.edu");
        PeerEvaluationUser john = user("j.smith@abc.edu");
        disable(john);

        this.mockMvc.perform(get(this.baseUrl + "/students/" + john.getId()).header(HttpHeaders.AUTHORIZATION, johnToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.flag").value(false))
                .andExpect(jsonPath("$.code").value(StatusCode.UNAUTHORIZED))
                .andExpect(jsonPath("$.message").value("User account is abnormal."));
    }

    @Test
    void testDeactivatedStudentCannotLogInAgain() throws Exception {
        disable(user("j.smith@abc.edu"));

        this.mockMvc.perform(post(this.baseUrl + "/users/login").with(httpBasic("j.smith@abc.edu", "123456")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(StatusCode.UNAUTHORIZED))
                .andExpect(jsonPath("$.message").value("User account is abnormal."));
    }

    @Test
    void testDeactivatedInstructorsStillValidTokenIsRefused() throws Exception {
        String billToken = login("b.gates@abc.edu");
        disable(user("b.gates@abc.edu"));

        this.mockMvc.perform(post(this.baseUrl + "/students/search").contentType(MediaType.APPLICATION_JSON).content("{}")
                        .header(HttpHeaders.AUTHORIZATION, billToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(StatusCode.UNAUTHORIZED))
                .andExpect(jsonPath("$.message").value("User account is abnormal."));
    }

}
