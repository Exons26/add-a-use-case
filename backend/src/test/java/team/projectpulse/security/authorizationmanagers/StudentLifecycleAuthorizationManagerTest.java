package team.projectpulse.security.authorizationmanagers;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authorization.AuthorizationResult;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import team.projectpulse.student.StudentSecurityService;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * BR-student-lifecycle: the course admin of the student's course, or an instructor of her course section, may
 * deactivate her; nobody else.
 */
@ExtendWith(MockitoExtension.class)
class StudentLifecycleAuthorizationManagerTest {

    @Mock
    StudentSecurityService studentSecurityService;

    @InjectMocks
    StudentLifecycleAuthorizationManager studentLifecycleAuthorizationManager;


    private AuthorizationResult authorize(Map<String, String> variables) {
        return this.studentLifecycleAuthorizationManager.authorize(() -> null, new RequestAuthorizationContext(mock(HttpServletRequest.class), variables));
    }

    @Test
    @DisplayName("Grants the course admin of the student's course")
    void grantsTheCourseAdmin() {
        given(this.studentSecurityService.isCurrentUserAdminOfStudentCourse(4)).willReturn(true);

        assertThat(authorize(Map.of("studentId", "4")).isGranted()).isTrue();
    }

    @Test
    @DisplayName("Grants an instructor of the student's course section")
    void grantsTheSectionInstructor() {
        given(this.studentSecurityService.isCurrentUserAdminOfStudentCourse(4)).willReturn(false);
        given(this.studentSecurityService.isCurrentUserInstructorOfStudentSection(4)).willReturn(true);

        assertThat(authorize(Map.of("studentId", "4")).isGranted()).isTrue();
    }

    @Test
    @DisplayName("Denies anyone who is neither (extension 3b)")
    void deniesEveryoneElse() {
        given(this.studentSecurityService.isCurrentUserAdminOfStudentCourse(4)).willReturn(false);
        given(this.studentSecurityService.isCurrentUserInstructorOfStudentSection(4)).willReturn(false);

        assertThat(authorize(Map.of("studentId", "4")).isGranted()).isFalse();
    }

    @Test
    @DisplayName("Denies when the bound studentId is missing or not a number")
    void deniesAnUnreadableId() {
        assertThat(authorize(Map.of()).isGranted()).isFalse();
        assertThat(authorize(Map.of("studentId", "x")).isGranted()).isFalse();
        verifyNoInteractions(this.studentSecurityService);
    }

}
