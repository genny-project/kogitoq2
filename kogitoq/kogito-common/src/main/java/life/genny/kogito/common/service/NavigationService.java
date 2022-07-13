package life.genny.kogito.common.service;

import java.util.Optional;
import java.util.Set;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.json.Json;
import javax.json.JsonObject;
import javax.json.bind.Jsonb;
import javax.json.bind.JsonbBuilder;

import org.jboss.logging.Logger;

import life.genny.qwandaq.attribute.Attribute;
import life.genny.qwandaq.attribute.EntityAttribute;
import life.genny.qwandaq.entity.BaseEntity;
import life.genny.qwandaq.exception.BadDataException;
import life.genny.qwandaq.message.QDataBaseEntityMessage;
import life.genny.qwandaq.models.UserToken;
import life.genny.qwandaq.utils.BaseEntityUtils;
import life.genny.qwandaq.utils.CacheUtils;
import life.genny.qwandaq.utils.CapabilityUtils;
import life.genny.qwandaq.utils.KafkaUtils;
import life.genny.qwandaq.utils.QwandaUtils;

@ApplicationScoped
public class NavigationService {

	private static final Logger log = Logger.getLogger(SearchService.class);

	Jsonb jsonb = JsonbBuilder.create();

	@Inject
	UserToken userToken;

	@Inject
	QwandaUtils qwandaUtils;

	@Inject
	BaseEntityUtils beUtils;

	@Inject
	CapabilityUtils capabilityUtils;

	@Inject
	SummaryService summaryService;

	@Inject
	SearchService searchService;

	public void defaultRedirect() {

		// TODO: This could alternatively fire the view workflow.

		BaseEntity user = beUtils.getUserBaseEntity();
		String defaultRedirectCode = user.getValueAsString("PRI_DEFAULT_REDIRECT");
		log.info("Actioning redirect for user " + user.getCode() + " : " + defaultRedirectCode);

		if (defaultRedirectCode == null) {
			log.error("User has no default redirect!");
			return;
		}

		if ("QUE_DASHBOARD_VIEW".equals(defaultRedirectCode)) {
			summaryService.sendSummary();
		} else {
			// default to table if not dashboard
			searchService.sendTable(defaultRedirectCode);
		}
	}

	/**
	 * Control main content navigation using a pcm and a question
	 *
	 * @param pcmCode The code of the PCM baseentity
	 * @param questionCode The code of the question
	 */
	public void navigateContent(final String pcmCode, final String questionCode) {

		// fetch and update content pcm
		BaseEntity content = beUtils.getBaseEntityByCode("PCM_CONTENT");
		try {
			content.setValue("PRI_LOC1", pcmCode);
		} catch (BadDataException e) {
			e.printStackTrace();
		}

		// fetch and update desired pcm
		BaseEntity pcm = beUtils.getBaseEntityByCode(pcmCode);
		Attribute attribute = qwandaUtils.getAttribute("PRI_QUESTION_CODE");
		EntityAttribute ea = new EntityAttribute(pcm, attribute, 1.0, questionCode);
		try {
			pcm.addAttribute(ea);
		} catch (BadDataException e) {
			e.printStackTrace();
		}

		// package all and send
		QDataBaseEntityMessage msg = new QDataBaseEntityMessage();
		msg.add(content);
		msg.add(pcm);
		msg.setToken(userToken.getToken());
		msg.setReplace(true);

		KafkaUtils.writeMsg("webdata", msg);
	}

	/**
	 * Send a view event.
	 *
	 * @param code The code of the view event.
	 * @param targetCode The targetCode of the view event.
	 */
	public void sendViewEvent(final String code, final String targetCode) {

		JsonObject json = Json.createObjectBuilder()
			.add("event_type", "VIEW")
			.add("msg_type", "EVT_MSG")
			.add("token", userToken.getToken())
			.add("data", Json.createObjectBuilder()
				.add("code", code)
				.add("targetCode", targetCode))
			.build();

		log.info("Sending View Event -> " + code + " : " + targetCode);

		KafkaUtils.writeMsg("events", json.toString());
	}

	/**
	 * Function to update a pcm location
	 */
    public void updatePcm(String pcmCode, String loc, String newValue) {

        log.info("Replacing " + pcmCode + ":" + loc + " with " + newValue);

        String cachedCode = userToken.getJTI() + ":" + pcmCode;
        BaseEntity pcm = CacheUtils.getObject(userToken.getProductCode(), cachedCode, BaseEntity.class);

        if (pcm == null) {
            log.info("Couldn't find " + cachedCode + " in cache, grabbing from db!");
            pcm = beUtils.getBaseEntityByCode(userToken.getProductCode(), pcmCode);
        }

        if (pcm == null) {
            log.error("Couldn't find PCM with code " + pcmCode);
            throw new NullPointerException("Couldn't find PCM with code " + pcmCode);
        }

        log.info("Found PCM " + pcm);

        Optional<EntityAttribute> locOptional = pcm.findEntityAttribute(loc);
        if (!locOptional.isPresent()) {
            log.error("Couldn't find base entity attribute " + loc);
            throw new NullPointerException("Couldn't find base entity attribute " + loc);
        }

        EntityAttribute locAttribute = locOptional.get();
        log.info(locAttribute.getAttributeCode() + " has valueString " + locAttribute.getValueString());
        locAttribute.setValueString(newValue);

        Set<EntityAttribute> attributes = pcm.getBaseEntityAttributes();
        attributes.removeIf(att -> att.getAttributeCode().equals(loc));
        attributes.add(locAttribute);
        pcm.setBaseEntityAttributes(attributes);

        QDataBaseEntityMessage msg = new QDataBaseEntityMessage(pcm);
        msg.setToken(userToken.getToken());
        msg.setReplace(true);
        KafkaUtils.writeMsg("webdata", msg);

        CacheUtils.putObject(userToken.getProductCode(), cachedCode, pcm);
    }
}
