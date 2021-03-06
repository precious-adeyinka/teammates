package teammates.test.cases.logic;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import teammates.common.datatransfer.CourseRoster;
import teammates.common.datatransfer.DataBundle;
import teammates.common.datatransfer.FeedbackParticipantType;
import teammates.common.datatransfer.StudentEnrollDetails;
import teammates.common.datatransfer.StudentUpdateStatus;
import teammates.common.datatransfer.UserRole;
import teammates.common.datatransfer.attributes.FeedbackQuestionAttributes;
import teammates.common.datatransfer.attributes.FeedbackResponseAttributes;
import teammates.common.datatransfer.attributes.FeedbackResponseCommentAttributes;
import teammates.common.datatransfer.attributes.FeedbackSessionAttributes;
import teammates.common.datatransfer.attributes.InstructorAttributes;
import teammates.common.datatransfer.attributes.StudentAttributes;
import teammates.common.datatransfer.questions.FeedbackQuestionType;
import teammates.common.exception.EntityAlreadyExistsException;
import teammates.common.exception.EntityDoesNotExistException;
import teammates.common.exception.InvalidParametersException;
import teammates.logic.core.FeedbackQuestionsLogic;
import teammates.logic.core.FeedbackResponseCommentsLogic;
import teammates.logic.core.FeedbackResponsesLogic;
import teammates.logic.core.FeedbackSessionsLogic;
import teammates.logic.core.StudentsLogic;
import teammates.storage.api.InstructorsDb;
import teammates.storage.api.StudentsDb;
import teammates.test.driver.AssertHelper;

/**
 * SUT: {@link FeedbackResponsesLogic}.
 */
public class FeedbackResponsesLogicTest extends BaseLogicTest {

    private static FeedbackSessionsLogic fsLogic = FeedbackSessionsLogic.inst();
    private static FeedbackQuestionsLogic fqLogic = FeedbackQuestionsLogic.inst();
    private static FeedbackResponsesLogic frLogic = FeedbackResponsesLogic.inst();
    private static FeedbackResponseCommentsLogic frcLogic = FeedbackResponseCommentsLogic.inst();
    private static DataBundle specialCharBundle = loadDataBundle("/SpecialCharacterTest.json");
    private static DataBundle questionTypeBundle = loadDataBundle("/FeedbackSessionQuestionTypeTest.json");

    @BeforeClass
    public void classSetup() {
        // extra test data used on top of typical data bundle
        removeAndRestoreDataBundle(specialCharBundle);
        removeAndRestoreDataBundle(questionTypeBundle);
    }

    @Test
    public void allTests() throws Exception {
        testIsNameVisibleTo();
        testGetViewableResponsesForQuestionInSection();
        testUpdateFeedbackResponse();
        testUpdateFeedbackResponsesForChangingTeam();
        testUpdateFeedbackResponsesForChangingTeam_deleteLastResponse_decreaseResponseRate();
        testUpdateFeedbackResponsesForChangingTeam_deleteNotLastResponse_sameResponseRate();
        testUpdateFeedbackResponsesForChangingEmail();
        testDeleteFeedbackResponsesForStudent();
        testSpecialCharactersInTeamName();
        testDeleteFeedbackResponsesForCourse();
    }

    private void testSpecialCharactersInTeamName() {
        ______TS("test special characters");

        FeedbackQuestionAttributes question = fqLogic.getFeedbackQuestion(
                "First Session", "FQLogicPCT.CS2104", 1);

        // Alice will see 4 responses
        assertEquals(4,
                frLogic.getViewableFeedbackResponsesForQuestionInSection(
                question,
                "FQLogicPCT.alice.b@gmail.tmt",
                UserRole.STUDENT,
                "First Session").size());

        // Benny will see 4 responses
        assertEquals(4,
                frLogic.getViewableFeedbackResponsesForQuestionInSection(
                question,
                "FQLogicPCT.benny.c@gmail.tmt",
                UserRole.STUDENT,
                "First Session").size());

        // Charlie will see 3 responses
        assertEquals(3,
                frLogic.getViewableFeedbackResponsesForQuestionInSection(
                question,
                "FQLogicPCT.charlie.d@gmail.tmt",
                UserRole.STUDENT,
                "First Session").size());

        // Danny will see 3 responses
        assertEquals(3,
                frLogic.getViewableFeedbackResponsesForQuestionInSection(
                question,
                "FQLogicPCT.danny.e@gmail.tmt",
                UserRole.STUDENT,
                "First Session").size());

        // Emily will see 1 response
        assertEquals(1,
                frLogic.getViewableFeedbackResponsesForQuestionInSection(
                question,
                "FQLogicPCT.emily.f@gmail.tmt",
                UserRole.STUDENT,
                "First Session").size());

    }

    private void testUpdateFeedbackResponse() throws Exception {

        ______TS("success: standard update with carried params ");

        FeedbackResponseAttributes responseToUpdate = getResponseFromDatastore("response1ForQ2S1C1");

        responseToUpdate.responseMetaData = "Updated Response";
        responseToUpdate.feedbackSessionName = "copy over";
        responseToUpdate.recipient = null;

        frLogic.updateFeedbackResponse(responseToUpdate);

        responseToUpdate = getResponseFromDatastore("response1ForQ2S1C1");
        responseToUpdate.responseMetaData = "Updated Response";

        assertEquals(responseToUpdate.toString(),
                frLogic.getFeedbackResponse(responseToUpdate.feedbackQuestionId, responseToUpdate.giver,
                responseToUpdate.recipient).toString());

        ______TS("failure: recipient one that is already exists");

        responseToUpdate = getResponseFromDatastore("response1ForQ2S1C1");

        FeedbackResponseAttributes existingResponse =
                new FeedbackResponseAttributes(
                        responseToUpdate.feedbackSessionName,
                        responseToUpdate.courseId,
                        responseToUpdate.feedbackQuestionId,
                        responseToUpdate.feedbackQuestionType,
                        responseToUpdate.giver,
                        responseToUpdate.giverSection,
                        "student3InCourse1@gmail.tmt",
                        responseToUpdate.recipientSection,
                        responseToUpdate.responseMetaData);

        frLogic.createFeedbackResponses(Arrays.asList(existingResponse));

        responseToUpdate.recipient = "student3InCourse1@gmail.tmt";

        FeedbackResponseAttributes[] finalResponse = new FeedbackResponseAttributes[] { responseToUpdate };
        EntityAlreadyExistsException eaee = assertThrows(EntityAlreadyExistsException.class,
                () -> frLogic.updateFeedbackResponse(finalResponse[0]));
        AssertHelper.assertContains("Trying to create a Feedback Response that exists", eaee.getMessage());

        ______TS("success: recipient changed to something else");

        responseToUpdate.recipient = "student5InCourse1@gmail.tmt";

        frLogic.updateFeedbackResponse(responseToUpdate);

        assertEquals(responseToUpdate.toString(),
                frLogic.getFeedbackResponse(responseToUpdate.feedbackQuestionId, responseToUpdate.giver,
                responseToUpdate.recipient).toString());
        assertNull(frLogic.getFeedbackResponse(
                responseToUpdate.feedbackQuestionId, responseToUpdate.giver, "student2InCourse1@gmail.tmt"));

        ______TS("success: both giver and recipient changed (teammate changed response)");

        responseToUpdate = getResponseFromDatastore("response1GracePeriodFeedback");
        responseToUpdate.giver = "student5InCourse1@gmail.tmt";
        responseToUpdate.recipient = "Team 1.1";

        assertNotNull(frLogic.getFeedbackResponse(
                responseToUpdate.feedbackQuestionId, "student4InCourse1@gmail.tmt", "Team 1.2"));

        frLogic.updateFeedbackResponse(responseToUpdate);

        assertEquals(responseToUpdate.toString(),
                frLogic.getFeedbackResponse(responseToUpdate.feedbackQuestionId, responseToUpdate.giver,
                responseToUpdate.recipient).toString());
        assertNull(frLogic.getFeedbackResponse(
                responseToUpdate.feedbackQuestionId, "student4InCourse1@gmail.tmt", "Team 1.2"));

        ______TS("failure: invalid params");

        // Cannot have invalid params as all possible invalid params
        // are copied over from an existing response.

        ______TS("failure: no such response");

        responseToUpdate.setId("invalidId");

        finalResponse[0] = responseToUpdate;
        EntityDoesNotExistException ednee = assertThrows(EntityDoesNotExistException.class,
                () -> frLogic.updateFeedbackResponse(finalResponse[0]));
        AssertHelper.assertContains(
                "Trying to update a feedback response that does not exist.",
                ednee.getMessage());
    }

    private void testUpdateFeedbackResponsesForChangingTeam() throws Exception {

        ______TS("standard update team case");

        StudentAttributes studentToUpdate = dataBundle.students.get("student4InCourse1");

        // Student 4 has 1 responses to him from team members,
        // 1 response from him a team member, and
        // 1 team response from him to another team.
        FeedbackQuestionAttributes teamQuestion = getQuestionFromDatastore("team.members.feedback");
        assertEquals(1,
                frLogic.getFeedbackResponsesForReceiverForQuestion(
                teamQuestion.getId(), studentToUpdate.email).size());
        assertEquals(1,
                frLogic.getFeedbackResponsesFromGiverForQuestion(
                teamQuestion.getId(), studentToUpdate.email).size());

        teamQuestion = getQuestionFromDatastore("team.feedback");
        assertEquals(1,
                frLogic.getFeedbackResponsesFromGiverForQuestion(
                teamQuestion.getId(), studentToUpdate.email).size());

        // Add one more non-team response
        FeedbackResponseAttributes responseToAdd =
                new FeedbackResponseAttributes("First feedback session", "idOfTypicalCourse1",
                                               getQuestionFromDatastore("qn1InSession1InCourse1").getId(),
                                               FeedbackQuestionType.TEXT, studentToUpdate.email, "Section 1",
                                               studentToUpdate.email, "Section 1", "New Response to self");
        frLogic.createFeedbackResponses(Arrays.asList(responseToAdd));

        // All these responses should be gone after he changes teams

        frLogic.updateFeedbackResponsesForChangingTeam(
                studentToUpdate.course, studentToUpdate.email, studentToUpdate.team, "Team 1.2");

        teamQuestion = getQuestionFromDatastore("team.members.feedback");
        assertEquals(0,
                frLogic.getFeedbackResponsesForReceiverForQuestion(
                teamQuestion.getId(), studentToUpdate.email).size());
        assertEquals(0,
                frLogic.getFeedbackResponsesFromGiverForQuestion(
                teamQuestion.getId(), studentToUpdate.email).size());

        teamQuestion = getQuestionFromDatastore("team.feedback");
        assertEquals(0,
                frLogic.getFeedbackResponsesForReceiverForQuestion(
                teamQuestion.getId(), studentToUpdate.email).size());

        // Non-team response should remain

        assertEquals(1,
                frLogic.getFeedbackResponsesFromGiverForQuestion(
                getQuestionFromDatastore("qn1InSession1InCourse1").getId(),
                studentToUpdate.email).size());

        ______TS("test updateFeedbackResponseForChangingTeam for recipient type = giver's team members including giver");
        FeedbackQuestionAttributes questionToTeamMembersAndSelf =
                                        getQuestionFromDatastore(questionTypeBundle, "qn1InContribSession2InCourse2");
        studentToUpdate = questionTypeBundle.students.get("student2InCourse2");
        FeedbackResponseAttributes responseToBeDeleted =
                getResponseFromDatastore(questionTypeBundle, "response1ForQ1ContribSession2Course2");
        StudentEnrollDetails studentDetails1 =
                new StudentEnrollDetails(StudentUpdateStatus.MODIFIED, studentToUpdate.course,
                                         studentToUpdate.email, studentToUpdate.team,
                                         studentToUpdate.team + "tmp", studentToUpdate.section,
                                         studentToUpdate.section + "tmp");

        assertNotNull(frLogic.getFeedbackResponse(questionToTeamMembersAndSelf.getId(),
                                                  responseToBeDeleted.giver,
                                                  responseToBeDeleted.recipient));
        assertTrue(frLogic.updateFeedbackResponseForChangingTeam(studentDetails1, responseToBeDeleted));
        assertNull(frLogic.getFeedbackResponse(questionToTeamMembersAndSelf.getId(),
                                               responseToBeDeleted.giver,
                                               responseToBeDeleted.recipient));

        // restore DataStore so other tests are unaffected
        restoreStudentFeedbackResponseToDatastore(responseToBeDeleted);
    }

    private void testUpdateFeedbackResponsesForChangingTeam_deleteLastResponse_decreaseResponseRate()
            throws Exception {
        FeedbackResponseAttributes responseToBeDeleted =
                getResponseFromDatastore(questionTypeBundle, "response1ForQ1ContribSession2Course2");
        // make sure it's the last response by the student
        assertEquals(1, numResponsesFromGiverInSession(responseToBeDeleted.giver,
                                                       responseToBeDeleted.feedbackSessionName,
                                                       responseToBeDeleted.courseId));
        StudentAttributes student = questionTypeBundle.students.get("student2InCourse2");
        StudentEnrollDetails enrollmentDetailsToTriggerDeletion =
                new StudentEnrollDetails(StudentUpdateStatus.MODIFIED, student.course,
                                         student.email, student.team, student.team + "tmp", student.section,
                                         student.section + "tmp");

        int originalResponseRate = getResponseRate(responseToBeDeleted.feedbackSessionName,
                                                   responseToBeDeleted.courseId);
        assertTrue(frLogic.updateFeedbackResponseForChangingTeam(enrollmentDetailsToTriggerDeletion,
                                                                 responseToBeDeleted));
        int responseRateAfterDeletion = getResponseRate(responseToBeDeleted.feedbackSessionName,
                                                        responseToBeDeleted.courseId);
        assertEquals(originalResponseRate - 1, responseRateAfterDeletion);

        // restore DataStore so other tests are unaffected
        restoreStudentFeedbackResponseToDatastore(responseToBeDeleted);
    }

    private void testUpdateFeedbackResponsesForChangingTeam_deleteNotLastResponse_sameResponseRate()
            throws Exception {
        FeedbackResponseAttributes responseToBeDeleted =
                getResponseFromDatastore(questionTypeBundle, "response1ForQ1S5C1");
        // make sure it's not the last response by the student
        assertTrue(1 < numResponsesFromGiverInSession(responseToBeDeleted.giver,
                                                      responseToBeDeleted.feedbackSessionName,
                                                      responseToBeDeleted.courseId));
        StudentAttributes student = questionTypeBundle.students.get("student1InCourse1");
        StudentEnrollDetails enrollmentDetailsToTriggerDeletion =
                new StudentEnrollDetails(StudentUpdateStatus.MODIFIED, student.course,
                                         student.email, student.team, student.team + "tmp", student.section,
                                         student.section + "tmp");

        int originalResponseRate = getResponseRate(responseToBeDeleted.feedbackSessionName,
                                                   responseToBeDeleted.courseId);
        assertTrue(frLogic.updateFeedbackResponseForChangingTeam(enrollmentDetailsToTriggerDeletion,
                                                                 responseToBeDeleted));
        int responseRateAfterDeletion = getResponseRate(responseToBeDeleted.feedbackSessionName,
                                                        responseToBeDeleted.courseId);
        assertEquals(originalResponseRate, responseRateAfterDeletion);

        // restore DataStore so other tests are unaffected
        restoreStudentFeedbackResponseToDatastore(responseToBeDeleted);
    }

    private int numResponsesFromGiverInSession(String studentEmail, String sessionName, String courseId) {
        int numResponses = 0;
        for (FeedbackResponseAttributes response : questionTypeBundle.feedbackResponses.values()) {
            if (response.giver.equals(studentEmail) && response.feedbackSessionName.equals(sessionName)
                    && response.courseId.equals(courseId)) {
                numResponses++;
            }
        }
        return numResponses;
    }

    private int getResponseRate(String sessionName, String courseId) {
        FeedbackSessionAttributes sessionFromDataStore = fsLogic.getFeedbackSession(sessionName, courseId);
        return sessionFromDataStore.getRespondingInstructorList().size()
                + sessionFromDataStore.getRespondingStudentList().size();
    }

    private void restoreStudentFeedbackResponseToDatastore(FeedbackResponseAttributes response)
            throws InvalidParametersException, EntityDoesNotExistException, EntityAlreadyExistsException {
        restoreFeedbackResponse(response);
        fsLogic.addStudentRespondent(response.giver, response.feedbackSessionName, response.courseId);
    }

    private void restoreFeedbackResponse(FeedbackResponseAttributes response)
            throws InvalidParametersException, EntityAlreadyExistsException, EntityDoesNotExistException {
        if (frLogic.getFeedbackResponse(response.getId()) == null) {
            frLogic.createFeedbackResponses(Arrays.asList(response));
        } else {
            frLogic.updateFeedbackResponse(response);
        }
    }

    private void testUpdateFeedbackResponsesForChangingEmail() throws Exception {
        ______TS("standard update email case");

        // Student 1 currently has 2 responses to him and 2 from himself.
        // Student 1 currently has 2 response comment for responses to him
        // and 1 response comment from responses from himself.
        StudentAttributes studentToUpdate = dataBundle.students.get("student1InCourse1");
        List<FeedbackResponseAttributes> responsesForReceiver =
                frLogic.getFeedbackResponsesForReceiverForCourse(
                        studentToUpdate.course, studentToUpdate.email);
        List<FeedbackResponseAttributes> responsesFromGiver =
                frLogic.getFeedbackResponsesFromGiverForCourse(
                        studentToUpdate.course, studentToUpdate.email);
        List<FeedbackResponseAttributes> responsesToAndFromStudent = new ArrayList<>();
        responsesToAndFromStudent.addAll(responsesForReceiver);
        responsesToAndFromStudent.addAll(responsesFromGiver);
        List<FeedbackResponseCommentAttributes> responseCommentsForStudent =
                getFeedbackResponseCommentsForResponsesFromDatastore(responsesToAndFromStudent);

        assertEquals(2, responsesForReceiver.size());
        assertEquals(2, responsesFromGiver.size());
        assertEquals(3, responseCommentsForStudent.size());

        frLogic.updateFeedbackResponsesForChangingEmail(
                studentToUpdate.course, studentToUpdate.email, "new@email.tmt");

        responsesForReceiver = frLogic.getFeedbackResponsesForReceiverForCourse(
                studentToUpdate.course, studentToUpdate.email);
        responsesFromGiver = frLogic.getFeedbackResponsesFromGiverForCourse(
                studentToUpdate.course, studentToUpdate.email);
        responsesToAndFromStudent = new ArrayList<>();
        responsesToAndFromStudent.addAll(responsesForReceiver);
        responsesToAndFromStudent.addAll(responsesFromGiver);
        responseCommentsForStudent =
                getFeedbackResponseCommentsForResponsesFromDatastore(responsesToAndFromStudent);

        assertEquals(0, responsesForReceiver.size());
        assertEquals(0, responsesFromGiver.size());
        assertEquals(0, responseCommentsForStudent.size());

        responsesForReceiver = frLogic.getFeedbackResponsesForReceiverForCourse(
                studentToUpdate.course, "new@email.tmt");
        responsesFromGiver = frLogic.getFeedbackResponsesFromGiverForCourse(
                studentToUpdate.course, "new@email.tmt");
        responsesToAndFromStudent = new ArrayList<>();
        responsesToAndFromStudent.addAll(responsesForReceiver);
        responsesToAndFromStudent.addAll(responsesFromGiver);
        responseCommentsForStudent =
                getFeedbackResponseCommentsForResponsesFromDatastore(responsesToAndFromStudent);

        assertEquals(2, responsesForReceiver.size());
        assertEquals(2, responsesFromGiver.size());
        assertEquals(3, responseCommentsForStudent.size());

        frLogic.updateFeedbackResponsesForChangingEmail(
                studentToUpdate.course, "new@email.tmt", studentToUpdate.email);
    }

    private void testGetViewableResponsesForQuestionInSection() throws Exception {

        ______TS("success: GetViewableResponsesForQuestion - instructor");

        InstructorAttributes instructor = dataBundle.instructors.get("instructor1OfCourse1");
        FeedbackQuestionAttributes fq = getQuestionFromDatastore("qn3InSession1InCourse1");
        List<FeedbackResponseAttributes> responses =
                frLogic.getViewableFeedbackResponsesForQuestionInSection(fq, instructor.email,
                                                                         UserRole.INSTRUCTOR, null);

        assertEquals(1, responses.size());

        ______TS("success: GetViewableResponsesForQuestionInSection - instructor");

        fq = getQuestionFromDatastore("qn2InSession1InCourse1");
        responses = frLogic.getViewableFeedbackResponsesForQuestionInSection(
                fq, instructor.email, UserRole.INSTRUCTOR, "Section 1");

        assertEquals(3, responses.size());

        responses = frLogic.getViewableFeedbackResponsesForQuestionInSection(
                fq, instructor.email, UserRole.INSTRUCTOR, "Section 2");

        assertEquals(0, responses.size());

        ______TS("success: GetViewableResponsesForQuestion - student");

        StudentAttributes student = dataBundle.students.get("student1InCourse1");
        fq = getQuestionFromDatastore("qn2InSession1InCourse1");
        responses = frLogic.getViewableFeedbackResponsesForQuestionInSection(fq, student.email, UserRole.STUDENT, null);

        assertEquals(2, responses.size());

        fq = getQuestionFromDatastore("qn3InSession1InCourse1");
        responses = frLogic.getViewableFeedbackResponsesForQuestionInSection(fq, student.email, UserRole.STUDENT, null);

        assertEquals(1, responses.size());

        fq.recipientType = FeedbackParticipantType.TEAMS;
        fq.showResponsesTo.add(FeedbackParticipantType.RECEIVER);
        fq.showResponsesTo.add(FeedbackParticipantType.RECEIVER_TEAM_MEMBERS);
        fq.showResponsesTo.remove(FeedbackParticipantType.STUDENTS);
        FeedbackResponseAttributes fr = getResponseFromDatastore("response1ForQ3S1C1");
        fr.recipient = student.email;
        frLogic.updateFeedbackResponse(fr);

        responses = frLogic.getViewableFeedbackResponsesForQuestionInSection(fq, student.email, UserRole.STUDENT, null);

        assertEquals(1, responses.size());

        ______TS("success: Null student in response, should skip over null student");
        fq = getQuestionFromDatastore("qn2InSession1InCourse1");
        fq.showResponsesTo.add(FeedbackParticipantType.RECEIVER_TEAM_MEMBERS);

        FeedbackResponseAttributes existingResponse = getResponseFromDatastore("response1ForQ2S1C1");

        //Create a "null" response to simulate trying to get a null student's response
        FeedbackResponseAttributes newResponse =
                new FeedbackResponseAttributes(
                        existingResponse.feedbackSessionName,
                        "nullCourse",
                        existingResponse.feedbackQuestionId,
                        existingResponse.feedbackQuestionType,
                        existingResponse.giver,
                        "Section 1",
                        "nullRecipient@gmail.tmt",
                        "Section 1",
                        existingResponse.responseMetaData);

        frLogic.createFeedbackResponses(Arrays.asList(newResponse));
        student = dataBundle.students.get("student2InCourse1");
        responses = frLogic.getViewableFeedbackResponsesForQuestionInSection(fq, student.email, UserRole.STUDENT, null);
        assertEquals(4, responses.size());

        ______TS("failure: GetViewableResponsesForQuestion invalid role");

        FeedbackQuestionAttributes finalFq = fq;
        AssertionError ae = assertThrows(AssertionError.class,
                () -> frLogic.getViewableFeedbackResponsesForQuestionInSection(
                        finalFq, instructor.email, UserRole.ADMIN, null));
        assertEquals("The role of the requesting use has to be Student or Instructor", ae.getMessage());
    }

    private void testIsNameVisibleTo() {

        ______TS("testIsNameVisibleTo");

        InstructorAttributes instructor = dataBundle.instructors.get("instructor1OfCourse1");
        StudentAttributes student = dataBundle.students.get("student1InCourse1");
        StudentAttributes student2 = dataBundle.students.get("student2InCourse1");
        StudentAttributes student3 = dataBundle.students.get("student3InCourse1");
        StudentAttributes student5 = dataBundle.students.get("student5InCourse1");

        FeedbackQuestionAttributes fq = getQuestionFromDatastore("qn3InSession1InCourse1");
        FeedbackResponseAttributes fr = getResponseFromDatastore("response1ForQ3S1C1");

        CourseRoster roster = new CourseRoster(
                new StudentsDb().getStudentsForCourse(fq.courseId),
                new InstructorsDb().getInstructorsForCourse(fq.courseId));

        assertTrue(frLogic.isNameVisibleToUser(fq, fr, instructor.email, UserRole.INSTRUCTOR, true, roster));
        assertTrue(frLogic.isNameVisibleToUser(fq, fr, instructor.email, UserRole.INSTRUCTOR, false, roster));
        assertTrue(frLogic.isNameVisibleToUser(fq, fr, student.email, UserRole.STUDENT, false, roster));

        ______TS("test if visible to own team members");

        fr.giver = student.email;
        assertTrue(frLogic.isNameVisibleToUser(fq, fr, student.email, UserRole.STUDENT, false, roster));

        ______TS("test if visible to receiver/reciever team members");

        fq.recipientType = FeedbackParticipantType.TEAMS;
        fq.showRecipientNameTo.clear();
        fq.showRecipientNameTo.add(FeedbackParticipantType.RECEIVER);
        fr.recipient = student.team;
        assertTrue(frLogic.isNameVisibleToUser(fq, fr, student.email, UserRole.STUDENT, false, roster));
        assertTrue(frLogic.isNameVisibleToUser(fq, fr, student3.email, UserRole.STUDENT, false, roster));

        fq.recipientType = FeedbackParticipantType.STUDENTS;
        fr.recipient = student.email;
        assertTrue(frLogic.isNameVisibleToUser(fq, fr, student.email, UserRole.STUDENT, false, roster));
        assertFalse(frLogic.isNameVisibleToUser(fq, fr, student2.email, UserRole.STUDENT, false, roster));

        fq.recipientType = FeedbackParticipantType.TEAMS;
        fq.showRecipientNameTo.clear();
        fq.showRecipientNameTo.add(FeedbackParticipantType.RECEIVER_TEAM_MEMBERS);
        fr.recipient = student.team;
        assertTrue(frLogic.isNameVisibleToUser(fq, fr, student.email, UserRole.STUDENT, false, roster));
        assertTrue(frLogic.isNameVisibleToUser(fq, fr, student3.email, UserRole.STUDENT, false, roster));

        fq.recipientType = FeedbackParticipantType.STUDENTS;
        fr.recipient = student.email;
        assertTrue(frLogic.isNameVisibleToUser(fq, fr, student.email, UserRole.STUDENT, false, roster));
        assertTrue(frLogic.isNameVisibleToUser(fq, fr, student2.email, UserRole.STUDENT, false, roster));
        assertFalse(frLogic.isNameVisibleToUser(fq, fr, student5.email, UserRole.STUDENT, false, roster));

        ______TS("test anonymous team recipients");
        // Only members of the recipient team should be able to see the recipient name
        fq.recipientType = FeedbackParticipantType.TEAMS;
        fq.showRecipientNameTo.clear();
        fq.showRecipientNameTo.add(FeedbackParticipantType.RECEIVER);
        fq.showResponsesTo.add(FeedbackParticipantType.STUDENTS);
        fr.recipient = "Team 1.1";
        assertFalse(frLogic.isNameVisibleToUser(fq, fr, student5.email, UserRole.STUDENT, false, roster));

        ______TS("null question");

        assertFalse(frLogic.isNameVisibleToUser(null, fr, student.email, UserRole.STUDENT, false, roster));

    }

    private void testDeleteFeedbackResponsesForStudent() throws Exception {

        ______TS("standard delete");

        StudentAttributes studentToDelete = dataBundle.students.get("student1InCourse1");
        List<FeedbackResponseAttributes> responsesForStudent1 =
                frLogic.getFeedbackResponsesFromGiverForCourse(studentToDelete.course, studentToDelete.email);
        responsesForStudent1
                .addAll(
                        frLogic.getFeedbackResponsesForReceiverForCourse(studentToDelete.course, studentToDelete.email));
        frLogic.deleteFeedbackResponsesForStudentAndCascade(studentToDelete.course, studentToDelete.email);

        List<FeedbackResponseAttributes> remainingResponses = new ArrayList<>();
        remainingResponses.addAll(
                frLogic.getFeedbackResponsesFromGiverForCourse(studentToDelete.course, studentToDelete.email));
        remainingResponses.addAll(
                frLogic.getFeedbackResponsesForReceiverForCourse(studentToDelete.course, studentToDelete.email));
        assertEquals(0, remainingResponses.size());

        List<FeedbackResponseCommentAttributes> remainingComments = new ArrayList<>();
        for (FeedbackResponseAttributes response : responsesForStudent1) {
            remainingComments.addAll(frcLogic.getFeedbackResponseCommentForResponse(response.getId()));
        }
        assertEquals(0, remainingComments.size());

        ______TS("shift team then delete");

        remainingResponses.clear();

        studentToDelete = dataBundle.students.get("student2InCourse1");

        studentToDelete.team = "Team 1.3";
        StudentsLogic.inst().updateStudentCascadeWithoutDocument(studentToDelete.email, studentToDelete);

        frLogic.deleteFeedbackResponsesForStudentAndCascade(studentToDelete.course, studentToDelete.email);

        remainingResponses.addAll(
                frLogic.getFeedbackResponsesFromGiverForCourse(studentToDelete.course, studentToDelete.email));
        remainingResponses.addAll(
                frLogic.getFeedbackResponsesForReceiverForCourse(studentToDelete.course, studentToDelete.email));
        assertEquals(0, remainingResponses.size());

        ______TS("delete last person in team");

        remainingResponses.clear();

        studentToDelete = dataBundle.students.get("student5InCourse1");

        frLogic.deleteFeedbackResponsesForStudentAndCascade(studentToDelete.course, studentToDelete.email);
        remainingResponses.addAll(
                frLogic.getFeedbackResponsesFromGiverForCourse(studentToDelete.course, studentToDelete.email));
        remainingResponses.addAll(
                frLogic.getFeedbackResponsesForReceiverForCourse(studentToDelete.course, studentToDelete.email));

        // check that team responses are gone too. already checked giver as it is stored by giver email not team id.
        remainingResponses.addAll(frLogic.getFeedbackResponsesForReceiverForCourse(studentToDelete.course, "Team 1.2"));

        assertEquals(0, remainingResponses.size());
    }

    private void testDeleteFeedbackResponsesForCourse() {
        ______TS("standard delete");

        // test that responses are deleted
        String courseId = "idOfTypicalCourse1";
        assertFalse(frLogic.getFeedbackResponsesForSession("First feedback session", courseId).isEmpty());
        assertFalse(frLogic.getFeedbackResponsesForSession("Grace Period Session", courseId).isEmpty());
        assertFalse(frLogic.getFeedbackResponsesForSession("Closed Session", courseId).isEmpty());
        frLogic.deleteFeedbackResponsesForCourse(courseId);

        assertEquals(0, frLogic.getFeedbackResponsesForSession("First feedback session", courseId).size());
        assertEquals(0, frLogic.getFeedbackResponsesForSession("Grace Period Session", courseId).size());
        assertEquals(0, frLogic.getFeedbackResponsesForSession("Closed Session", courseId).size());

        // test that responses from other courses are unaffected
        String otherCourse = "idOfTypicalCourse2";
        assertFalse(frLogic.getFeedbackResponsesForSession("Instructor feedback session", otherCourse).isEmpty());
    }

    private FeedbackQuestionAttributes getQuestionFromDatastore(DataBundle dataBundle, String jsonId) {
        FeedbackQuestionAttributes questionToGet = dataBundle.feedbackQuestions.get(jsonId);
        questionToGet = fqLogic.getFeedbackQuestion(questionToGet.feedbackSessionName,
                                                    questionToGet.courseId,
                                                    questionToGet.questionNumber);

        return questionToGet;
    }

    private FeedbackQuestionAttributes getQuestionFromDatastore(String jsonId) {
        return getQuestionFromDatastore(dataBundle, jsonId);
    }

    private FeedbackResponseAttributes getResponseFromDatastore(DataBundle dataBundle, String jsonId) {
        FeedbackResponseAttributes response =
                                        dataBundle.feedbackResponses.get(jsonId);

        String qnId;
        try {
            int qnNumber = Integer.parseInt(response.feedbackQuestionId);
            qnId = fqLogic.getFeedbackQuestion(response.feedbackSessionName, response.courseId, qnNumber).getId();
        } catch (NumberFormatException e) {
            qnId = response.feedbackQuestionId;
        }

        return frLogic.getFeedbackResponse(
                qnId, response.giver, response.recipient);
    }

    private FeedbackResponseAttributes getResponseFromDatastore(String jsonId) {
        return getResponseFromDatastore(dataBundle, jsonId);
    }

    private List<FeedbackResponseCommentAttributes> getFeedbackResponseCommentsForResponsesFromDatastore(
            List<FeedbackResponseAttributes> responses) {
        List<FeedbackResponseCommentAttributes> responseComments = new ArrayList<>();
        for (FeedbackResponseAttributes response : responses) {
            List<FeedbackResponseCommentAttributes> responseCommentsForResponse =
                    frcLogic.getFeedbackResponseCommentForResponse(response.getId());
            responseComments.addAll(responseCommentsForResponse);
        }
        return responseComments;
    }
}
