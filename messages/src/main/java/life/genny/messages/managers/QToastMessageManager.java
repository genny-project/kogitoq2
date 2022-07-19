package life.genny.messages.managers;

import javax.inject.Inject;
import java.util.Map;

import javax.enterprise.context.ApplicationScoped;
import javax.json.bind.Jsonb;
import javax.json.bind.JsonbBuilder;

import org.jboss.logging.Logger;

import life.genny.qwandaq.entity.BaseEntity;
import life.genny.qwandaq.message.QCommandMessage;
import life.genny.qwandaq.utils.MergeUtils;
import life.genny.qwandaq.utils.BaseEntityUtils;
import life.genny.qwandaq.utils.KafkaUtils;

import life.genny.qwandaq.models.UserToken;

@ApplicationScoped
public class QToastMessageManager implements QMessageProvider {
	
	private static final Logger log = Logger.getLogger(QToastMessageManager.class);

	static Jsonb jsonb = JsonbBuilder.create();

	@Inject
	BaseEntityUtils beUtils;

	@Inject
	UserToken userToken;

	@Override
	public void sendMessage(BaseEntity templateBe, Map<String, Object> contextMap) {
		
		log.info("About to send TOAST message!");
		
		BaseEntity target = (BaseEntity) contextMap.get("RECIPIENT");

		if (target == null) {
			log.error("Target is NULL");
			return;
		}

		// Check for Toast Body
		String body = null;
		if (contextMap.containsKey("BODY")) {
			body = (String) contextMap.get("BODY");
		} else {
			body = templateBe.getValue("PRI_BODY", null);
		}
		if (body == null) {
			log.error("body is NULL");
			return;
		}

		// Check for Toast Style
		String style = null;
		if (contextMap.containsKey("STYLE")) {
			style = (String) contextMap.get("STYLE");
		} else {
			style = templateBe.getValue("PRI_STYLE", "INFO");
		}
		if (style == null) {
			log.error("style is NULL");
			return;
		}

		// Mail Merging Data
		body = MergeUtils.merge(body, contextMap);

		// build toast command msg
		QCommandMessage msg = new QCommandMessage("TOAST", style);
		msg.setMessage(body);
		msg.setToken(userToken.getToken());

		// send to frontend
		KafkaUtils.writeMsg("webcmds", msg);
	}

}
