import org.junit.jupiter.api.Test;
import udpapi.ParseReply;
import udpapi.reply.Reply;
import udpapi.reply.ReplyStatus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

public class ParseReplyTest {

    @Test
    public void Should_CorrectlyParse_LoginCommandReply() {
        testReply("auth-1 200 pyWUs 95.143.55.96:3333 LOGIN ACCEPTED\n", reply -> {
            assertEquals("auth-1", reply.getFullTag());
            assertEquals(ReplyStatus.LOGIN_ACCEPTED, reply.getReplyStatus());
            assertEquals("pyWUs", reply.getResponseData().getFirst());
        });
    }

    @Test
    public void Should_CorrectlyParse_LogoutCommandReply() {
        testReply("logout-2 203 LOGGED OUT\n", reply -> {
            assertEquals("logout-2", reply.getFullTag());
            assertEquals(ReplyStatus.LOGGED_OUT, reply.getReplyStatus());
        });
    }

    @Test
    public void Should_CorrectlyParse_FileCommandReply() {
        String[] expectedResponseData = {"2570297","12743","194767","11111","0","","0","1","9d68fcae","10","very high","Blu-ray","OPUS","HEVC","1920x1080","japanese","english","90","1515024000","Yuru Camp - C1 - Opening - [Hi10](9d68fcae).mkv","","12","12","2018-2018","TV Series","","Yuru Camp","ゆるキャン△","Laid-Back Camp","ゆるキャン△'Laid-Back Camp'유루 캠프'مخيم الاسترخاء'摇曳露营△","C1","Opening","","","Hi10 Anime","Hi10"};
        testReply(
                "file:0-0 220 FILE\n2570297|12743|194767|11111|0||0|1|9d68fcae|10|very high|Blu-ray|OPUS|HEVC|1920x1080|japanese|english|90|1515024000|Yuru Camp - C1 - Opening - [Hi10](9d68fcae).mkv||12|12|2018-2018|TV Series||Yuru Camp|ゆるキャン△|Laid-Back Camp|ゆるキャン△'Laid-Back Camp'유루 캠프'مخيم الاسترخاء'摇曳露营△|C1|Opening|||Hi10 Anime|Hi10\n"
                , reply -> {
            assertEquals("file:0-0", reply.getFullTag());
            assertEquals(ReplyStatus.FILE, reply.getReplyStatus());
            assertEquals(expectedResponseData.length, reply.getResponseData().size());
            for (int i = 0; i < expectedResponseData.length; i++) {
                assertEquals(expectedResponseData[i], reply.getResponseData().get(i));
            }
        });
    }

    @Test
    public void Should_CorrectlyParse_MyListCommandReply() {
        String[] expectedResponseData = {"389792755"};
        testReply(
                "mladd:0-2 210 MYLIST ENTRY ADDED\n389792755\n"
                , reply -> {
                    assertEquals("mladd:0-2", reply.getFullTag());
                    assertEquals(ReplyStatus.MYLIST_ENTRY_ADDED, reply.getReplyStatus());
                    assertEquals(expectedResponseData.length, reply.getResponseData().size());
                    for (int i = 0; i < expectedResponseData.length; i++) {
                        assertEquals(expectedResponseData[i], reply.getResponseData().get(i));
                    }
                });
    }

    @Test
    public void Should_CorrectlyParse_BannedReply() {
        testReply(
                "555 BANNED\nLeech\n"
                , reply -> {
                    assertNull(reply.getFullTag());
                    assertEquals(ReplyStatus.BANNED, reply.getReplyStatus());
                });
    }

    private void testReply(String reply, ParseReply.Integration callback) {
        var spyCallback = spy(new TestIntegration(callback));
        new ParseReply(spyCallback, reply).run();
        verify(spyCallback).addReply(any());
    }

    private static class TestIntegration implements ParseReply.Integration {
        private final ParseReply.Integration callback;

        public TestIntegration(ParseReply.Integration callback) {
            this.callback = callback;
        }

        @Override
        public void addReply(Reply reply) {
            callback.addReply(reply);
        }
    }
}
