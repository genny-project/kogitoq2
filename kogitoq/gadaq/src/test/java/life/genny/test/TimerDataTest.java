package life.genny.test;

import javax.json.bind.Jsonb;
import javax.json.bind.JsonbBuilder;

import org.jboss.logging.Logger;
import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.ExtractableResponse;
import io.restassured.response.Response;
import life.genny.kogito.common.models.TimerData;
import life.genny.kogito.common.models.TimerEvent;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.CoreMatchers.notNullValue;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.concurrent.TimeUnit;

@QuarkusTest
public class TimerDataTest {
    private static final Logger log = Logger.getLogger(TimerDataTest.class);

    Jsonb jsonb = JsonbBuilder.create();

    @Test
    public void TimerEventTest() {
        log.info("This is the TimerEventTest");
        log.info("This is the TimerEventTest2");

        TimerEvent t1 = new TimerEvent(0L, "TEST_TIMER_1", "LNK_WARNING");
        log.info("TimerEvent1 = " + t1);

        TimerEvent t2 = new TimerEvent(5L, "TEST_TIMER_2", "LNK_DANGER");
        log.info("TimerEvent2 = " + t2);

        TimerEvent t3 = new TimerEvent(7L, "TEST_TIMER_3", "LNK_DEAD");
        log.info("TimerEvent3 = " + t3);

        TimerData timerData = new TimerData();
        // test sorting
        timerData.add(t1);
        timerData.add(t3);
        timerData.add(t2);

        log.info("Timer Data = " + timerData);

        timerData.updateElapsed();

        for (int t = 0; t < 100; t++) {
            Long currentTimeStampUTC = LocalDateTime.now().atZone(
                    ZoneId.of("UTC")).withZoneSameInstant(ZoneId.of("UTC")).toEpochSecond();
            if (timerData.isMilestone()) {
                log.info(currentTimeStampUTC + " , elapsed:" + timerData.getElapsedMin() + ", isMilestone is "
                        + (timerData.isMilestone() ? "TRUE" : "FALSE")
                        + ",isExpired is " + (timerData.hasExpired() ? "TRUE" : "FALSE") + ", "
                        + timerData.getNextMilestone());

                timerData.updateMilestone();
                log.info("");
            }
            try {
                TimeUnit.SECONDS.sleep(10);
            } catch (InterruptedException e) {

            }
            timerData.updateElapsed();
        }

    }
}