package com.weathernerd.permissions.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.weathernerd.permissions.domain.UserAgentRoleClassifier;
import com.weathernerd.permissions.service.PermissionPolicy;
import com.weathernerd.permissions.service.PermissionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(PermissionsController.class)
@Import({PermissionService.class, PermissionPolicy.class, UserAgentRoleClassifier.class})
class PermissionsControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @Test
    void returnsEmpty200ForChromeWithoutRequestBody() throws Exception {
        String userAgent = "Mozilla/5.0 Chrome/125.0.0.0 Safari/537.36";

        mockMvc.perform(post("/permissions/is_granted")
                        .header(HttpHeaders.USER_AGENT, userAgent))
                .andExpect(status().isOk())
                .andExpect(content().string(""));
    }

    @Test
    void returnsEmpty200ForSafari() throws Exception {
        String userAgent = "Mozilla/5.0 (Macintosh; Intel Mac OS X 14_0) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Safari/605.1.15";

        mockMvc.perform(post("/permissions/is_granted")
                        .header(HttpHeaders.USER_AGENT, userAgent))
                .andExpect(status().isOk())
                .andExpect(content().string(""));
    }

    @Test
    void returns403JsonForOtherBrowser() throws Exception {
        String userAgent = "Mozilla/5.0 (Windows NT 10.0) AppleWebKit/537.36 Chrome/125.0.0.0 Safari/537.36 Edg/125.0.0.0";

        mockMvc.perform(post("/permissions/is_granted")
                        .header(HttpHeaders.USER_AGENT, userAgent))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(content().json("{\"error\":\"Permission denied\"}", true));
    }

    @Test
    void treatsMissingUserAgentAsDenied() throws Exception {
        mockMvc.perform(post("/permissions/is_granted"))
                .andExpect(status().isForbidden())
                .andExpect(content().json("{\"error\":\"Permission denied\"}", true));
    }

    @Test
    void returnsProblemDetailsWhenMethodIsNotSupported() throws Exception {
        mockMvc.perform(get("/permissions/is_granted"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(405))
                .andExpect(jsonPath("$.title").value("Method not allowed"));
    }
}
