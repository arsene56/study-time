package com.studytime.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ApiSecurityIntegrationTest {
    private static final String PARENT_TOKEN = "study-time-demo-parent-mom-v1";
    private static final String XIAOMAN_TOKEN = "study-time-demo-student-xiaoman-v1";

    @Autowired
    MockMvc mockMvc;

    @Test
    void rejectsRequestsWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/demo/context"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().exists("X-Request-ID"))
                .andExpect(jsonPath("$.requestId").isNotEmpty());
    }

    @Test
    void parentCanReadStudentsWithinTheFamily() throws Exception {
        mockMvc.perform(get("/api/v1/students/demo-student-keke/activities")
                        .header(AUTHORIZATION, "Bearer " + PARENT_TOKEN))
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Request-ID"));
    }

    @Test
    void studentCanReadOwnDataButNotAnotherStudentsData() throws Exception {
        mockMvc.perform(get("/api/v1/students/demo-student-xiaoman/activities")
                        .header(AUTHORIZATION, "Bearer " + XIAOMAN_TOKEN))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/students/demo-student-keke/activities")
                        .header(AUTHORIZATION, "Bearer " + XIAOMAN_TOKEN))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("无权访问其他学生的数据"));
    }

    @Test
    void studentCannotPerformParentOnlyOperations() throws Exception {
        mockMvc.perform(post("/api/v1/students/demo-student-xiaoman/weekly-goal")
                        .header(AUTHORIZATION, "Bearer " + XIAOMAN_TOKEN)
                        .contentType("application/json")
                        .content("{\"targetTasks\":5,\"targetFocusMinutes\":60,\"bonusStars\":10}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void requestCannotSelfReportAnAuditIdentity() throws Exception {
        mockMvc.perform(post("/api/v1/students/demo-student-xiaoman/weekly-goal")
                        .header(AUTHORIZATION, "Bearer " + PARENT_TOKEN)
                        .contentType("application/json")
                        .content("{\"targetTasks\":5,\"targetFocusMinutes\":60,\"bonusStars\":10,\"actorName\":\"伪造身份\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("请求内容格式不正确或包含不支持的字段"));
    }

    @Test
    void studentContextDoesNotExposeAnotherStudent() throws Exception {
        mockMvc.perform(get("/api/v1/demo/context")
                        .header(AUTHORIZATION, "Bearer " + XIAOMAN_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.students.length()").value(1))
                .andExpect(jsonPath("$.students[0].id").value("demo-student-xiaoman"));
    }

    @Test
    void apiResponsesIncludeSecurityHeaders() throws Exception {
        mockMvc.perform(get("/api/v1/demo/context")
                        .header(AUTHORIZATION, "Bearer " + PARENT_TOKEN))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Security-Policy", "default-src 'none'; frame-ancestors 'none'"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"));
    }
}
