package com.mathverse;

import static org.assertj.core.api.Assertions.assertThat;

import com.mathverse.service.DemoData;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.JsonNode;

/** Port of backend/tests/test_guardians.py. */
class GuardiansTest extends ApiTest {

    @Autowired DemoData demoData;

    Response link(Account guardian, String code) {
        return postJson("/guardians/links", Map.of("link_code", code), guardian);
    }

    Response report(Account viewer, long studentId) {
        return getPath("/students/" + studentId + "/report", viewer);
    }

    static String code(Account student) {
        return student.user().get("link_code").asString();
    }

    static List<String> field(JsonNode list, String name) {
        List<String> values = new ArrayList<>();
        list.forEach(item -> values.add(item.get(name).asString()));
        return values;
    }

    // ── T10: parent and teacher access restrictions ──────────────────────────

    // test_guardian_sees_only_linked_students
    @ParameterizedTest
    @ValueSource(strings = {"parent", "teacher"})
    void guardianSeesOnlyLinkedStudents(String role) {
        Account child = register("student", "Child");
        Account stranger = register("student", "Stranger");
        Account guardian = register(role);

        assertThat(report(guardian, child.id()).status()).isEqualTo(403); // not linked yet
        assertThat(link(guardian, code(child)).status()).isEqualTo(201);
        assertThat(report(guardian, child.id()).status()).isEqualTo(200);
        assertThat(report(guardian, stranger.id()).status()).isEqualTo(403);
        assertThat(report(guardian, 99999).status()).isEqualTo(403);
    }

    // test_linking_needs_the_students_code
    @Test
    void linkingNeedsTheStudentsCode() {
        Account child = register("student");
        Account guardian = register("parent");
        assertThat(link(guardian, "SPIDER-WRONG1").status()).isEqualTo(404);
        assertThat(link(guardian, String.valueOf(child.id())).status()).isIn(404, 422); // an id is not a code
        assertThat(getPath("/guardians/students", guardian).text()).isEqualTo("[]");
    }

    // test_students_cannot_use_guardian_endpoints_or_read_each_other
    @Test
    void studentsCannotUseGuardianEndpointsOrReadEachOther() {
        Account a = register("student");
        Account b = register("student");
        assertThat(link(a, code(b)).status()).isEqualTo(403);
        assertThat(getPath("/guardians/students", a).status()).isEqualTo(403);
        assertThat(report(a, b.id()).status()).isEqualTo(403);
        assertThat(report(a, a.id()).status()).isEqualTo(200);
    }

    // test_guardian_cannot_link_to_another_guardian
    @Test
    void guardianCannotLinkToAnotherGuardian() {
        Account parent = register("parent");
        Account teacher = register("teacher");
        assertThat(teacher.user().get("link_code").isNull()).isTrue();
        assertThat(link(parent, "SPIDER-AAAAAA").status()).isEqualTo(404);
    }

    // ── T12: several children linked to one parent ───────────────────────────

    // test_parent_with_several_children
    @Test
    void parentWithSeveralChildren() {
        Account parent = register("parent");
        Account first = register("student", "Asha");
        Account second = register("student", "Ben");
        Account third = register("student", "Chen");
        winMission(first); // only the first child has played

        for (Account child : List.of(first, second)) {
            assertThat(link(parent, code(child)).status()).isEqualTo(201);
        }
        assertThat(link(parent, code(first)).status()).isEqualTo(201); // linking twice is harmless

        JsonNode listed = getPath("/guardians/students", parent).json();
        assertThat(field(listed, "name")).containsExactly("Asha", "Ben");

        JsonNode firstReport = report(parent, first.id()).json();
        JsonNode secondReport = report(parent, second.id()).json();
        assertThat(firstReport.get("questions_answered").asInt()).isEqualTo(7);
        assertThat(firstReport.get("missions_won").asInt()).isEqualTo(1);
        assertThat(secondReport.get("questions_answered").asInt()).isZero();
        assertThat(secondReport.get("accuracy_pct").isNull()).isTrue();
        assertThat(report(parent, third.id()).status()).isEqualTo(403);
    }

    // test_one_child_can_have_a_parent_and_a_teacher
    @Test
    void oneChildCanHaveAParentAndATeacher() {
        Account child = register("student");
        for (Account guardian : List.of(register("parent"), register("teacher"))) {
            assertThat(link(guardian, code(child)).status()).isEqualTo(201);
            assertThat(report(guardian, child.id()).status()).isEqualTo(200);
        }
    }

    // ── Demo isolation: demo and real accounts never mix ─────────────────────

    // test_demo_and_real_accounts_cannot_be_linked
    @Test
    void demoAndRealAccountsCannotBeLinked() {
        settings.setDemoMode(true);
        demoData.createDemoAccounts();
        Account realChild = register("student");
        Account realParent = register("parent");

        JsonNode demoParentBody = postEmpty("/auth/demo/parent").json();
        Account demoParent = new Account(demoParentBody.get("access_token").asString(), demoParentBody.get("user"),
                demoParentBody.get("user").get("id").asLong());
        long demoStudentId = postEmpty("/auth/demo/student").json().get("user").get("id").asLong();

        // a demo guardian cannot attach to, or read, a real child
        assertThat(link(demoParent, code(realChild)).status()).isEqualTo(404);
        assertThat(report(demoParent, realChild.id()).status()).isEqualTo(403);

        // a real guardian cannot attach to, or read, the demo child
        assertThat(link(realParent, "SPIDER-DEMO01").status()).isEqualTo(404);
        assertThat(report(realParent, demoStudentId).status()).isEqualTo(403);

        // the demo guardian still sees the demo child
        JsonNode listed = getPath("/guardians/students", demoParent).json();
        assertThat(listed.size()).isEqualTo(1);
        assertThat(listed.get(0).get("id").asLong()).isEqualTo(demoStudentId);
        assertThat(listed.get(0).get("is_demo").asBoolean()).isTrue();
    }

    // ── Java-only checks ─────────────────────────────────────────────────────

    @Test
    void codesAreMatchedWithoutCaseOrSpaces() {
        Account child = register("student", "Child");
        Account parent = register("parent");
        Response res = link(parent, "  " + code(child).toLowerCase() + " ");
        assertThat(res.status()).isEqualTo(201);
        assertThat(res.json().get("name").asString()).isEqualTo("Child");
        assertThat(res.json().get("accuracy_pct").isNull()).isTrue();
        assertThat(res.json().get("last_active").isNull()).isTrue();
    }

    @Test
    void reportWithAnInvalidIdOrOffsetIsRejected() {
        Account student = register();
        assertThat(getPath("/students/abc/report", student).status()).isEqualTo(422);
        assertThat(getPath("/students/" + student.id() + "/report?tz_offset_minutes=abc", student).status())
                .isEqualTo(422);
        assertThat(getPath("/students/" + student.id() + "/report?tz_offset_minutes=-841", student).status())
                .isEqualTo(422);
        assertThat(getPath("/students/" + student.id() + "/report?tz_offset_minutes=-840", student).status())
                .isEqualTo(200);
    }
}
