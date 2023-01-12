package life.genny.kogito.common.messages;

import life.genny.qwandaq.entity.BaseEntity;
import life.genny.qwandaq.message.QBaseMSGMessageType;
import life.genny.qwandaq.models.UserToken;

import org.jboss.logging.Logger;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.json.bind.Jsonb;
import javax.json.bind.JsonbBuilder;
import java.util.Map;
import java.util.HashMap;

@ApplicationScoped
public class SendMessageService {

	private static final Logger log = Logger.getLogger(SendMessageService.class);

	Jsonb jsonb = JsonbBuilder.create();

	@Inject
	UserToken userToken;

	/**
	 * Send a genny message.
	 *
	 * @param templateCode    The template to use
	 * @param recipientBEJson The recipient BaseEntity json
	 */
	public void sendMessage(String templateCode, String recipientBECode) {
		new SendMessage(templateCode, recipientBECode).sendMessage();
	}

	/**
	 * Send a genny message.
	 *
	 * @param templateCode    The template to use
	 * @param recipientBEJson The recipient BaseEntity json
	 * @param ctxMap          The map of contexts to use
	 */
	public void sendMessage(String templateCode, String recipientBECode, Map<String, String> ctxMap) {
		new SendMessage(templateCode, recipientBECode, ctxMap).sendMessage();
	}

	/**
	 * Send a genny message.
	 *
	 * @param templateCode    The template to use
	 * @param recipientBEJson The recipient BaseEntity json
	 * @param ctxMap          The map of contexts to use
	 */
	public void sendMessage(String templateCode, BaseEntity recipientBE, Map<String, String> ctxMap) {
		new SendMessage(templateCode, recipientBE, ctxMap).sendMessage();
	}

	public void sendMessage(String templateCode, BaseEntity recipientBE, String url) {
		new SendMessage(templateCode, recipientBE, url).sendMessage();
	}

	/**
	 * Send a toast message and cannot use message type as a parameter via kogito
	 *
	 * @param templateCode    The template to use
	 * @param recipientBECode The recipient BaseEntity code
	 * @param entityCode The base entity is created
	 * @param msgType The message type might be TOAST,SLACK,SMS,EMAIL,SENDGRID,VOICE
	 * @param ctxMap The context map
	 */
	public void sendMessage(String templateCode,String recipientBECode,String entityCode,String msgType,Map<String, String> ctxMap) {
		if(msgType.contains(QBaseMSGMessageType.TOAST.name())) {
			new SendMessage(templateCode, recipientBECode, entityCode, QBaseMSGMessageType.TOAST, ctxMap).sendMessage();
		}
		if(msgType.contains(QBaseMSGMessageType.SLACK.name())) {
			ctxMap.put("RECIPIENT",templateCode);
			new SendMessage(templateCode, recipientBECode, entityCode, QBaseMSGMessageType.SLACK, ctxMap).sendMessage();
		}
		if(msgType.contains(QBaseMSGMessageType.SMS.name())) {
			new SendMessage(templateCode, recipientBECode, entityCode, QBaseMSGMessageType.SMS, ctxMap).sendMessage();
		}
		if(msgType.contains(QBaseMSGMessageType.EMAIL.name())) {
			new SendMessage(templateCode, recipientBECode, entityCode, QBaseMSGMessageType.EMAIL, ctxMap).sendMessage();
		}
		if(msgType.contains(QBaseMSGMessageType.SENDGRID.name())) {
			new SendMessage(templateCode, recipientBECode, entityCode, QBaseMSGMessageType.SENDGRID, ctxMap).sendMessage();
		}
		if(msgType.contains(QBaseMSGMessageType.VOICE.name())) {
			new SendMessage(templateCode, recipientBECode, entityCode, QBaseMSGMessageType.VOICE, ctxMap).sendMessage();
		}
	}

	/**
	 * Send all genny messages for a given milestone code and coreBE code but check
	 * Injects.
	 * 
	 * @param milestoneCode The workflow location to send messages for
	 *
	 * @param coreBEcode    The core BaseEntity code for which all Contexts can be
	 *                      derived.
	 */
	public void sendAllMessagesCodeNullCheck(String milestoneCode, String coreBeCode) {
		if (userToken == null) {
			log.error("NULL USER TOKEN - Aborting Sending Messages");
			return;
		}
		new SendAllMessages(milestoneCode, coreBeCode).sendMessage();
	}

	/**
	 * Send all genny messages for a given milestone code and coreBE code.
	 * 
	 * @param milestoneCode The workflow location to send messages for
	 *
	 * @param coreBEcode    The core BaseEntity code for which all Contexts can be
	 *                      derived.
	 */
	public void sendAllMessagesCode(String milestoneCode, String coreBeCode) {
		new SendAllMessages(milestoneCode, coreBeCode).sendMessage();
	}

	/**
	 * Send all genny messages for a given milestone code.
	 * 
	 * @param milestoneCode The workflow location to send messages for
	 *
	 * @param coreBEJson    The core BaseEntity json for which all Contexts can be
	 *                      derived.
	 */
	public void sendAllMessagesJson(String milestoneCode, String coreBEJson) {
		// TODO: This is ugly. I Need to change this bit later.
		log.info("For milestoneCode : " + milestoneCode + " with the coreBEJson:" + coreBEJson);
		BaseEntity coreBE = jsonb.fromJson(coreBEJson, BaseEntity.class);
		String productCode = coreBE.getRealm();
		log.info("productCode is " + productCode);

		new SendAllMessages(productCode, milestoneCode, coreBE).sendMessage();
	}

	/**
	 * Send all genny messages for a given milestone code.
	 * 
	 * @param productCode.  The productCode to use.
	 * @param milestoneCode The workflow location to send messages for
	 *
	 * @param coreBE        The core BaseEntity for which all Contexts can be
	 *                      derived.
	 */
	public void sendAllMessages(String productCode, String milestoneCode, BaseEntity coreBE) {
		new SendAllMessages(productCode, milestoneCode, coreBE).sendMessage();
	}
}