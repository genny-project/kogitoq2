package life.genny.messages.managers;

import com.twilio.Twilio;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.type.PhoneNumber;
import life.genny.qwandaq.attribute.EntityAttribute;
import life.genny.qwandaq.entity.BaseEntity;
import life.genny.qwandaq.models.ANSIColour;
import life.genny.qwandaq.utils.MergeUtils;
import org.jboss.logging.Logger;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.util.Map;

@ApplicationScoped
public final class QSMSMessageManager extends QMessageProvider {

    public static final String MESSAGE_BOTH_DRIVER_OWNER = "BOTH";

    private static final Logger log = Logger.getLogger(QSMSMessageManager.class);

    @Inject
    MergeUtils mergeUtils;

    @Override
    public void sendMessage(BaseEntity templateBe, Map<String, Object> contextMap) {

        log.info(ANSIColour.GREEN + ">>>>>>>>>>>About to trigger SMS<<<<<<<<<<<<<<" + ANSIColour.RESET);

        BaseEntity projectBe = (BaseEntity) contextMap.get("PROJECT");
        BaseEntity target = (BaseEntity) contextMap.get("RECIPIENT");

        if (target == null) {
            log.error(ANSIColour.RED + "Target is NULL" + ANSIColour.RESET);
            return;
        }
        if (projectBe == null) {
            log.error(ANSIColour.RED + "ProjectBe is NULL" + ANSIColour.RESET);
            return;
        }

        String targetMobile = target.getValue("PRI_MOBILE", null);
        if (targetMobile == null) {
            log.error(ANSIColour.RED + "TargetMobile is NULL" + ANSIColour.RESET);
            return;
        }

        String body = null;
        if (contextMap.containsKey("BODY")) {
            body = (String) contextMap.get("BODY");
        } else {
            body = templateBe.getValue("PRI_BODY", null);
        }
        if (body == null) {
            log.error(ANSIColour.RED + "Body is NULL" + ANSIColour.RESET);
            return;
        }

        // Mail Merging Data
        body = mergeUtils.merge(body, contextMap);

        //target is toPhoneNumber, Source is the fromPhoneNumber
        String productCode = projectBe.getRealm();
        String projectBeCode = projectBe.getCode();
        EntityAttribute twilioAccountSidAttribute = beaUtils.getEntityAttribute(productCode, projectBeCode, "ENV_TWILIO_ACCOUNT_SID");
        String accountSID = twilioAccountSidAttribute != null ? twilioAccountSidAttribute.getValueString() : null;
        EntityAttribute twilioSourcePhoneAttribute = beaUtils.getEntityAttribute(productCode, projectBeCode, "ENV_TWILIO_SOURCE_PHONE");
        String sourcePhone = twilioSourcePhoneAttribute != null ? twilioSourcePhoneAttribute.getValueString() : null;
        EntityAttribute twilioAuthTokenAttribute = beaUtils.getEntityAttribute(productCode, projectBeCode, "ENV_TWILIO_AUTH_TOKEN");
        String twilioAuthToken = twilioAuthTokenAttribute != null ? twilioAuthTokenAttribute.getValueString() : null;

        // Debug logs for devs
        log.debug("accountSID = " + accountSID);
        log.debug("sourcePhone = " + sourcePhone);
        log.debug("twilioAuthToken = " + twilioAuthToken);
        log.debug("targetMobile = " + targetMobile);

        if (accountSID != null && sourcePhone != null && twilioAuthToken != null) {

            // Use Try-Catch Block to ensure consumer does not die upon error.
            try {
                // Init and Send SMS
                Twilio.init(accountSID, twilioAuthToken);
                Message msg = Message.creator(new PhoneNumber(targetMobile), new PhoneNumber(sourcePhone), body).create();

                // Log response
                log.info("message status:" + msg.getStatus() + ", message SID:" + msg.getSid());
                log.info(ANSIColour.GREEN + " SMS Sent to " + targetMobile + ANSIColour.RESET);

            } catch (Exception e) {
                log.error(ANSIColour.RED + "Could Not Send SMS!!! Exception:" + e + ANSIColour.RESET);
            }

        } else {
            log.error(ANSIColour.RED + "Twilio credentials not loaded into cache" + ANSIColour.RESET);
        }

    }

}
