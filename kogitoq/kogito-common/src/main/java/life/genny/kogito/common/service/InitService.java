package life.genny.kogito.common.service;

import java.util.*;

import java.util.stream.Collectors;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import org.jboss.logging.Logger;

import life.genny.qwandaq.Ask;
import life.genny.qwandaq.Question;
import life.genny.qwandaq.attribute.Attribute;
import life.genny.qwandaq.constants.Prefix;
import life.genny.qwandaq.datatype.capability.core.CapabilitySet;
import life.genny.qwandaq.datatype.capability.requirement.ReqConfig;
import life.genny.qwandaq.datatype.capability.requirement.ReqConfig;
import life.genny.qwandaq.entity.BaseEntity;
import life.genny.qwandaq.entity.search.SearchEntity;
import life.genny.qwandaq.entity.search.trait.Filter;
import life.genny.qwandaq.entity.search.trait.Operator;
import life.genny.qwandaq.kafka.KafkaTopic;
import life.genny.qwandaq.managers.CacheManager;
import life.genny.qwandaq.managers.capabilities.CapabilitiesManager;
import life.genny.qwandaq.message.QDataAskMessage;
import life.genny.qwandaq.message.QDataAttributeMessage;
import life.genny.qwandaq.message.QDataBaseEntityMessage;
import life.genny.qwandaq.models.UserToken;
import life.genny.qwandaq.utils.*;
import org.jboss.logging.Logger;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.json.bind.Jsonb;
import javax.json.bind.JsonbBuilder;
import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * A Service class used for Auth Init operations.
 *
 * @auther Bryn Meachem
 * @author Jasper Robison
 */
@ApplicationScoped
public class InitService extends KogitoService {

	@Inject
	private CacheManager cm;

	@Inject
	private BaseEntityUtils beUtils;

	@Inject
	private UserToken userToken;

	@Inject
	private QwandaUtils qwandaUtils;

	@Inject
	private SearchUtils searchUtils;

	@Inject
	private CapabilitiesManager capMan;

	@Inject
	CacheManager cacheManager;

	@Inject
	Logger log;

	/**
	 * Send the Project BaseEntity.
	 */
	public void sendProject() {

		BaseEntity project = beUtils.getProjectBaseEntity();
		log.info("Sending Project " + project.getCode());

		// configure msg and send
		QDataBaseEntityMessage msg = new QDataBaseEntityMessage(project);
		msg.setToken(userToken.getToken());
		msg.setAliasCode("PROJECT");
		KafkaUtils.writeMsg(KafkaTopic.WEBDATA, msg);
	}

	/**
	 * Send the User.
	 */
	public void sendUser() {

		// fetch the users baseentity
		BaseEntity user = beUtils.getUserBaseEntity();
		log.info("Sending User " + user.getCode());

		// configure msg and send
		QDataBaseEntityMessage msg = new QDataBaseEntityMessage(user);
		msg.setToken(userToken.getToken());
		msg.setAliasCode("USER");

		KafkaUtils.writeMsg(KafkaTopic.WEBDATA, msg);
	}

	/**
	 * Send All attributes for the productCode.
	 */
	public void sendAllAttributes() {
		log.info("Sending Attributes for " + userToken.getProductCode());
		String productCode = userToken.getProductCode();

		Collection<Attribute> allAttributes = cacheManager.getAttributes(productCode);

		int BATCH_SIZE = 500;
		int count = 0;
		int batchNum = 1;
		int totalAttributesCount = allAttributes.size();
		int totalBatches = totalAttributesCount / BATCH_SIZE;
		if (totalAttributesCount % BATCH_SIZE != 0) {
			totalBatches++;
		}
		log.infof("%s Attribute(s) to be sent in %s batch(es).", totalAttributesCount, totalBatches);
		List<Attribute> attributesBatch = new LinkedList<>();
		for(Attribute attribute : allAttributes) {
			if (attribute.getCode().startsWith(Prefix.CAP_)) {
				continue;
			}
			attributesBatch.add(attribute);
			count++;
			if (count == BATCH_SIZE) {
				dispatchAttributesToKafka(attributesBatch, batchNum, totalBatches);
				attributesBatch.clear();
				count = 0;
				batchNum++;
			}
		}
		// Dispatch the last batch, if any
		if (!attributesBatch.isEmpty()) {
			dispatchAttributesToKafka(attributesBatch, batchNum, totalBatches);
		}
	}

	private void dispatchAttributesToKafka(List<Attribute> attributesBatch, int batchNum, int totalBatches) {
		QDataAttributeMessage msg = new QDataAttributeMessage();
		msg.add(attributesBatch);
		msg.setItems(attributesBatch);
		// set token and send
		msg.setToken(userToken.getToken());
		msg.setAliasCode("ATTRIBUTE_MESSAGE_BATCH_" + batchNum + "_OF_" + totalBatches);
		KafkaUtils.writeMsg(KafkaTopic.WEBDATA, msg);
	}

	/**
	 * Send PCM BaseEntities.
	 */
	public void sendPCMs() {

		log.info("Sending PCMs for " + userToken.getProductCode());

		String productCode = userToken.getProductCode();
		BaseEntity user = beUtils.getUserBaseEntity();
		CapabilitySet userCapabilities = capMan.getUserCapabilities(user);

		// get pcms using search
		SearchEntity searchEntity = new SearchEntity("SBE_PCMS", "PCM Search")
				.add(new Filter(Attribute.PRI_CODE, Operator.LIKE, "PCM_%"))
				.setAllColumns(true)
				.setPageSize(1000)
				.setRealm(productCode);

		log.info(jsonb.toJson(searchEntity));

		List<BaseEntity> pcms = searchUtils.searchBaseEntitys(searchEntity);
		if (pcms == null) {
			log.info("No PCMs found for " + productCode);
			return;
		}

		log.info("Sending " + pcms.size() + " PCMs");

		// configure ask msg
		QDataAskMessage askMsg = new QDataAskMessage();
		askMsg.setToken(userToken.getToken());
		askMsg.setReplace(true);
		askMsg.setAliasCode("PCM_INIT_ASK_MESSAGE");

		Set<Ask> asks = pcms.stream()
				.peek((pcm) -> log.info("Processing ".concat(pcm.getCode())))
				.map((pcm) -> {
					String questionCode = pcm.getValue("PRI_QUESTION_CODE", null);
					if (questionCode == null) {
						log.warn("(" + pcm.getCode() + " :: " + pcm.getName() + ") null PRI_QUESTION_CODE");
						return null;
					}

					Question question = cm.getQuestion(productCode, questionCode);
					if(!question.requirementsMet(userCapabilities)) {
						log.warn("[!] User does not meet capability requirements for question: " + questionCode);
						return null;
					} else {
						log.info("Passed Capabilities check: " + CommonUtils.getArrayString(question.getCapabilityRequirements()));
					}

					Ask ask = qwandaUtils.generateAskFromQuestion(question, user, user, userCapabilities, new ReqConfig());
					if (ask == null) {
						log.warn("(" + pcm.getCode() + " :: " + pcm.getName() + ") No asks found for " + question.getCode());
					}

					return ask;
				})
				// filter all pcms set to null by the map (this stops us having to query for questionCode twice, saving processing
				.filter((pcm) -> pcm != null)
				.collect(Collectors.toSet());

		askMsg.setItems(asks);

		KafkaUtils.writeMsg(KafkaTopic.WEBDATA, askMsg);

		// configure msg and send
		QDataBaseEntityMessage msg = new QDataBaseEntityMessage(pcms);
		msg.setToken(userToken.getToken());
		msg.setReplace(true);
		msg.setAliasCode("PCM_INIT_MESSAGE");
		KafkaUtils.writeMsg(KafkaTopic.WEBDATA, msg);
	}

	public void sendDrafts() {

		BaseEntity user = beUtils.getUserBaseEntity();
		Ask ask = qwandaUtils.generateAskFromQuestionCode("QUE_DRAFTS_GRP", user, user, new CapabilitySet(user), new ReqConfig());

		// configure msg and send
		QDataAskMessage msg = new QDataAskMessage(ask);
		msg.setToken(userToken.getToken());
		msg.setReplace(true);

		KafkaUtils.writeMsg(KafkaTopic.WEBDATA, msg);
	}

}
