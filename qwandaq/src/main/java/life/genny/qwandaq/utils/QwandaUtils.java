package life.genny.qwandaq.utils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.json.bind.Jsonb;
import javax.json.bind.JsonbBuilder;
import javax.persistence.NoResultException;

import org.apache.commons.lang3.StringUtils;
import org.jboss.logging.Logger;

import life.genny.qwandaq.Answer;
import life.genny.qwandaq.Ask;
import life.genny.qwandaq.Question;
import life.genny.qwandaq.QuestionQuestion;
import life.genny.qwandaq.attribute.Attribute;
import life.genny.qwandaq.attribute.EntityAttribute;
import life.genny.qwandaq.entity.BaseEntity;
import life.genny.qwandaq.entity.SearchEntity;
import life.genny.qwandaq.exception.runtime.BadDataException;
import life.genny.qwandaq.exception.runtime.ItemNotFoundException;
import life.genny.qwandaq.exception.runtime.NullParameterException;
import life.genny.qwandaq.graphql.ProcessData;
import life.genny.qwandaq.message.QDataAskMessage;
import life.genny.qwandaq.message.QDataAttributeMessage;
import life.genny.qwandaq.message.QDataBaseEntityMessage;
import life.genny.qwandaq.models.GennySettings;
import life.genny.qwandaq.models.UserToken;

/**
 * A utility class to assist in any Qwanda Engine Question
 * and Answer operations.
 * 
 * @author Jasper Robison
 */
@ApplicationScoped
public class QwandaUtils {

	public static final String[] ACCEPTED_PREFIXES = { "PRI_", "LNK_" };
	public static final String[] EXCLUDED_ATTRIBUTES = { "PRI_SUBMIT" };

	static final Logger log = Logger.getLogger(QwandaUtils.class);

	private final ExecutorService executorService = Executors.newFixedThreadPool(GennySettings.executorThreadCount());

	static Jsonb jsonb = JsonbBuilder.create();

	@Inject
	DatabaseUtils databaseUtils;

	@Inject
	DefUtils defUtils;

	@Inject
	SearchUtils searchUtils;

	@Inject
	BaseEntityUtils beUtils;

	@Inject
	UserToken userToken;

	public QwandaUtils() {
	}

	// Deliberately package private!
	Attribute saveAttribute(final Attribute attribute) {

		String productCode = userToken.getProductCode();
		Attribute existingAttrib = CacheUtils.getObject(productCode, attribute.getCode(), Attribute.class);

		if (existingAttrib != null) {
			if (CommonUtils.compare(attribute, existingAttrib)) {
				log.warn("Attribute already exists with same fields: " + existingAttrib.getCode());
				return existingAttrib;
			}
			log.info("Updating existing attribute!: " + existingAttrib.getCode());
		}

		CacheUtils.putObject(productCode, attribute.getCode(), attribute);
		databaseUtils.saveAttribute(attribute);

		return CacheUtils.getObject(productCode, attribute.getCode(), Attribute.class);
	}

	/**
	 * Get an attribute from the in memory attribute map. If productCode not found,
	 * it
	 * will try to fetch attributes from the DB.
	 *
	 * @param attributeCode the code of the attribute to get
	 * @return Attribute
	 */
	public Attribute getAttribute(final String attributeCode) {

		String productCode = userToken.getProductCode();
		Attribute attribute = CacheUtils.getObject(productCode, attributeCode, Attribute.class);

		if (attribute == null) {
			log.error("Could not find attribute " + attributeCode + " in cache: " + productCode);
			loadAllAttributesIntoCache(productCode);
		}

		attribute = CacheUtils.getObject(productCode, attributeCode, Attribute.class);

		if (attribute == null) {
			throw new ItemNotFoundException(productCode, attributeCode);
		}

		return attribute;
	}

	/**
	 * Load all attributes into the cache from the database.
	 *
	 * @param productCode The product of the attributes to initialize
	 */
	public void loadAllAttributesIntoCache(String productCode) {

		if (StringUtils.isBlank(productCode)) {
			log.error("RECEIVED NULL PRODUCT CODE WHILE LOADING ATTRIBUTES INTO CACHE!");
		}

		Long attributeCount = databaseUtils.countAttributes(productCode);
		final Integer CHUNK_LOAD_SIZE = 200;

		final int TOTAL_PAGES = (int) Math.ceil(attributeCount / CHUNK_LOAD_SIZE);

		Long totalAttribsCached = 0L;

		log.info("About to load all attributes for productCode " + productCode);
		log.info("Found " + attributeCount + " attributes");

		CacheUtils.putObject(productCode, "ATTRIBUTE_PAGES", TOTAL_PAGES);

		try {
			for (int currentPage = 0; currentPage < TOTAL_PAGES + 1; currentPage++) {

				QDataAttributeMessage msg = new QDataAttributeMessage();

				int attributesLoaded = currentPage * CHUNK_LOAD_SIZE;

				// Correctly determine how many more attributes we need to load in
				int nextLoad = CHUNK_LOAD_SIZE;
				if (attributeCount - attributesLoaded < CHUNK_LOAD_SIZE) {
					nextLoad = (int) (attributeCount - attributesLoaded);
				}

				List<Attribute> attributeList = databaseUtils.findAttributes(productCode, attributesLoaded, nextLoad,
						null);
				long lastMemory = PerformanceUtils.getMemoryUsage("MEGABYTES");

				log.info("Loading in page " + currentPage + " of " + TOTAL_PAGES + " containing " + nextLoad
						+ " attributes");
				log.info("Current memory usage: " + lastMemory + "MB");

				for (Attribute attribute : attributeList) {
					String key = attribute.getCode();
					CacheUtils.putObject(productCode, key, attribute);
					totalAttribsCached++;
				}
				long currentMemory = PerformanceUtils.getMemoryUsage(PerformanceUtils.MemoryMeasurement.MEGABYTES);
				long memoryUsed = currentMemory - lastMemory;

				log.info("Post load memory usage: " + currentMemory + "MB");
				log.info("Used up: " + memoryUsed + "MB");
				log.info("Percentage: " + PerformanceUtils.getPercentMemoryUsed() * 100f);
				log.info("============================");
				// NOTE: Warning, this may cause OOM errors.
				msg.add(attributeList);

				if (attributeList.size() > 0) {
					log.debug("Start AttributeID:"
							+ attributeList.get(0).getId() + ", End AttributeID:"
							+ attributeList.get(attributeList.size() - 1).getId());
				}

				CacheUtils.putObject(productCode, "ATTRIBUTES_P" + currentPage, msg);
			}

			log.info("Cached " + totalAttribsCached + " attributes");
		} catch (Exception e) {
			log.error("Error loading attributes for productCode: " + productCode);
			e.printStackTrace();
		}
	}

	/**
	 * Generate an ask for a question using the question code, the
	 * source and the target. This operation is recursive if the
	 * question is a group.
	 *
	 * @param code   The code of the question
	 * @param source The source entity
	 * @param target The target entity
	 * @return The generated Ask
	 */
	public Ask generateAskFromQuestionCode(final String code, final BaseEntity source, final BaseEntity target) {

		if (code == null)
			throw new NullParameterException("code");
		if (source == null)
			throw new NullParameterException("source");
		if (target == null)
			throw new NullParameterException("target");

		// if the code is QUE_BASEENTITY_GRP then display all the attributes
		if ("QUE_BASEENTITY_GRP".equals(code)) {
			return generateAskGroupUsingBaseEntity(target);
		}

		String productCode = userToken.getProductCode();
		log.debug("Fetching Question: " + code);

		// find the question in the database
		Question question;
		try {
			question = databaseUtils.findQuestionByCode(productCode, code);
		} catch (NoResultException e) {
			throw new ItemNotFoundException(code, e);
		}

		// init new parent ask
		Ask ask = new Ask(question);
		ask.setSourceCode(source.getCode());
		ask.setTargetCode(target.getCode());
		ask.setRealm(productCode);

		Attribute attribute = question.getAttribute();

		// override with Attribute icon if question icon is null
		if (attribute != null && attribute.getIcon() != null) {
			if (question.getIcon() == null) {
				question.setIcon(attribute.getIcon());
			}
		}

		// check if it is a question group
		if (question.getAttributeCode().startsWith(Question.QUESTION_GROUP_ATTRIBUTE_CODE)) {

			log.info("[*] Parent Question: " + question.getCode());

			// fetch questionQuestions from the DB
			List<QuestionQuestion> questionQuestions = databaseUtils.findQuestionQuestionsBySourceCode(productCode,
					question.getCode());

			// recursively operate on child questions
			for (QuestionQuestion questionQuestion : questionQuestions) {

				log.info("   [-] Found Child Question in database:  " + questionQuestion.getSourceCode() + ":"
						+ questionQuestion.getTargetCode());

				Ask child = generateAskFromQuestionCode(questionQuestion.getTargetCode(), source, target);

				// Do not include PRI_SUBMIT
				if ("PRI_SUBMIT".equals(child.getAttributeCode())) {
					continue;
				}

				// set boolean fields
				child.setMandatory(questionQuestion.getMandatory());
				child.setDisabled(questionQuestion.getDisabled());
				child.setHidden(questionQuestion.getHidden());
				child.setDisabled(questionQuestion.getDisabled());
				child.setReadonly(questionQuestion.getReadonly());

				// override with QuestionQuestion icon if exists
				if (questionQuestion.getIcon() != null) {
					child.getQuestion().setIcon(questionQuestion.getIcon());
				}

				ask.addChildAsk(child);
			}
		}

		return ask;
	}

	/**
	 * Recursively set the processId down through an ask tree.
	 *
	 * @param ask       The ask to traverse
	 * @param processId The processId to set
	 */
	public void recursivelySetProcessId(Ask ask, String processId) {

		ask.setProcessId(processId);

		if (ask.getChildAsks() != null) {
			for (Ask child : ask.getChildAsks()) {
				recursivelySetProcessId(child, processId);
			}
		}
	}

	/**
	 * Get all attribute codes active within an ask using recursion.
	 *
	 * @param codes The set of codes to add to
	 * @param ask   The ask to traverse
	 * @return The udpated set of codes
	 */
	public Set<String> recursivelyGetAttributeCodes(Set<String> codes, Ask ask) {

		String code = ask.getAttributeCode();

		// grab attribute code of current ask if conditions met
		if (!Arrays.asList(ACCEPTED_PREFIXES).contains(code.substring(0, 4))) {
			log.debugf("Prefix %s not in accepted list", code.substring(0, 4));
		} else if (Arrays.asList(EXCLUDED_ATTRIBUTES).contains(code)) {
			log.debugf("Attribute %s in exclude list", code);
		} else if (ask.getReadonly()) {
			log.debugf("Ask %s is set to readonly", ask.getQuestionCode());
		} else {
			codes.add(code);
		}

		// grab all child ask attribute codes
		if (ask.hasChildren()) {
			for (Ask childAsk : ask.getChildAsks()) {
				codes.addAll(recursivelyGetAttributeCodes(codes, childAsk));
			}
		}
		return codes;
	}

	private Map<String, Ask> getAllAsksRecursively(Ask ask) {
		return getAllAsksRecursively(ask, new HashMap<String, Ask>());
	}

	private Map<String, Ask> getAllAsksRecursively(Ask ask, Map<String, Ask> asks) {
		if(ask.hasChildren()) {
			for(Ask childAsk : ask.getChildAsks()) {
				asks.put(childAsk.getAttributeCode(), childAsk);
				asks = getAllAsksRecursively(childAsk, asks);
			}
		}
		return asks;
	}

	public boolean hasDepsAnswered(BaseEntity target, String[] dependencies) {
		target.getBaseEntityAttributes().stream().forEach(ea -> log.info(ea.getAttributeCode() + " = " + ea.getValue()));
		for (String d : dependencies) {
			if (!target.getValue(d).isPresent()) {
				return false;
			}
		}
		return true;
	}

  public Ask updateDependentAsks(Ask ask, BaseEntity target, BaseEntity defBE) {
		Map<String, Ask> flatMapAsks = getAllAsksRecursively(ask);
    return updateDependentAsks(ask, target, defBE, flatMapAsks);
  }

	public Ask updateDependentAsks(Ask ask, BaseEntity target, BaseEntity defBE, Map<String, Ask> flatMapAsks) {
		List<EntityAttribute> dependentAsks = defBE.findPrefixEntityAttributes("DEP");

		for (EntityAttribute dep : dependentAsks) {
			String attributeCode = StringUtils.removeStart(dep.getAttributeCode(), "DEP_");
			Ask targetAsk = flatMapAsks.get(attributeCode);
			if(targetAsk == null) {
				continue;
			}
			
			String[] dependencies = beUtils.cleanUpAttributeValue(dep.getValueString()).split(",");

			boolean depsAnswered = hasDepsAnswered(target, dependencies);
			targetAsk.setDisabled(!depsAnswered);
			targetAsk.setHidden(!depsAnswered);
		}
		
		return ask;
	}

	/**
	 * Check if all Ask mandatory fields are answered for a list of asks
	 * @param ask The ask to check
	 * @param answers The list of answers to check against
	 * @return Boolean
	 */
	public Boolean mandatoryFieldsAreAnswered(Ask ask, List<Answer> answers) {

		log.infof("Checking %s mandatorys against %s answers", ask.getQuestionCode(), answers.size());

		// find all the mandatory booleans
		Set<Ask> set = recursivelyFillFlatSet(new HashSet<Ask>(), ask);
		Boolean answered = true;

		// iterate entity attributes to check which have been answered
		for (Ask a : set) {

			String value = null;

			if (a.isMandatory()) {
				// try find value in answers
				for (Answer answer : answers) {
					if (answer.getAttributeCode().equals(a.getAttributeCode())) {
						value = answer.getValue();
					}
				}

				// if any are both blank and mandatory, then task is not complete
				if (StringUtils.isBlank(value)) {
					answered = false;
				}
			}

			String resultLine = String.format("%s : %s : %s", (a.isMandatory() ? "[M]" : "[O]"), a.getAttributeCode(), value);
			log.info("===> " + resultLine);
		}

		log.info("Mandatory fields are " + (answered ? "ALL" : "not") + " complete");

		return answered;
	}

	/**
	 * Fill the flat set of asks using recursion.
	 * @param set The set to fill
	 * @param ask The ask to traverse
	 * @return The filled set
	 */
	public Set<Ask> recursivelyFillFlatSet(Set<Ask> set, Ask ask) {

		// add current ask attribute code to map
		set.add(ask);

		// ensure child asks is not null
		if (ask.getChildAsks() == null) {
			return set;
		}

		// recursively add child ask attribute codes
		for (Ask child : ask.getChildAsks()) {
			set = recursivelyFillFlatSet(set, child);
		}

		return set;
	}

	/**
	 * Find the submit ask using recursion.
	 *
	 * @param ask      The ask to traverse
	 * @param disabled Should the submit ask be disabled
	 * @return The updated ask
	 */
	public Ask recursivelyFindAndUpdateSubmitDisabled(Ask ask, Boolean disabled) {

		// return ask if submit is found
		if (ask.getQuestion().getAttribute().getCode().equals("EVT_SUBMIT")) {
			log.info("[@] Setting PRI_SUBMIT disabled = " + disabled);
			ask.setDisabled(disabled);
		}

		// recursively check child asks for submit
		if (ask.getChildAsks() != null) {
			for (Ask child : ask.getChildAsks()) {
				child = recursivelyFindAndUpdateSubmitDisabled(child, disabled);
			}
		}

		return ask;
	}

	/**
	 * Send Submit enable/disable.
	 *
	 * @param ask     The ask message representing the questions
	 * @param enable. Enable the submit button
	 * @return Boolean representing whether the submit button was enabled
	 */
	public Boolean sendSubmit(Ask ask, Boolean enable) {

		// find the submit ask
		recursivelyFindAndUpdateSubmitDisabled(ask, !enable);

		QDataAskMessage msg = new QDataAskMessage(ask);
		msg.setToken(userToken.getToken());
		msg.setReplace(true);
		KafkaUtils.writeMsg("webcmds", msg);

		return enable;
	}

	/**
	 * Save process data to cache.
	 * @param processData The data to save
	 */
	public void storeProcessData(ProcessData processData) {

		String productCode = userToken.getProductCode();
		String key = String.format("%s:PROCESS_DATA", processData.getProcessId()); 

		CacheUtils.putObject(productCode, key, processData);
		log.infof("ProcessData cached to %s", key);
	}

	/**
	 * clear process data from cache.
	 * @param processId The id of the data to clear
	 */
	public void clearProcessData(String processId) {

		String productCode = userToken.getProductCode();
		String key = String.format("%s:PROCESS_DATA", processId); 

		CacheUtils.removeEntry(productCode, key);
		log.infof("ProcessData removed from cache: %s", key);
	}

	/**
	 * Fetch process data from cache.
	 * @param processId The id of the data to fetch
	 * @return The saved data
	 */
	public ProcessData fetchProcessData(String processId) {
		
		String productCode = userToken.getProductCode();
		String key = String.format("%s:PROCESS_DATA", processId); 

		return CacheUtils.getObject(productCode, key, ProcessData.class);
	}

	/**
	 * Setup the process entity used to store task data.
	 *
	 * @param processData The process data
	 * @return The updated process entity
	 */
	public BaseEntity generateProcessEntity(ProcessData processData) {

		String targetCode = processData.getTargetCode();
		List<String> attributeCodes = processData.getAttributeCodes();

		// init entity and force the realm
		log.info("Creating Process Entity " + processData.getProcessEntityCode() + "...");
		BaseEntity processEntity = new BaseEntity(processData.getProcessEntityCode(), "QuestionBE");
		processEntity.setRealm(userToken.getProductCode());

		BaseEntity target = beUtils.getBaseEntity(targetCode);

		log.info("Found " + attributeCodes.size() + " active attributes in asks");

		// add an entityAttribute to process entity for each attribute
		for (String code : attributeCodes) {

			// check for existing attribute in target
			EntityAttribute ea = target.findEntityAttribute(code).orElseGet(() -> {

				// otherwise create new attribute
				Attribute attribute = getAttribute(code);
				Object value = null;
				// default toggles to false
				String className = attribute.getDataType().getClassName();
				if (className.contains("Boolean") || className.contains("bool"))
					value = false;

				return new EntityAttribute(processEntity, attribute, 1.0, value);
			});

			processEntity.addAttribute(ea);
		}

		log.info("ProcessBE contains " + processEntity.getBaseEntityAttributes().size() + " entity attributes");

		return processEntity;
	}

	/**
	 * Save an {@link Answer} object.
	 *
	 * @param answer The answer to save
	 * @return The target BaseEntity
	 */
	public BaseEntity saveAnswer(Answer answer) {

		List<BaseEntity> targets = saveAnswers(Arrays.asList(answer));

		if (targets != null && targets.size() > 0) {
			return targets.get(0);
		}

		return null;
	}

	/**
	 * Save a List of {@link Answer} objects.
	 *
	 * @param answers The list of answers to save
	 * @return The target BaseEntitys
	 */
	public List<BaseEntity> saveAnswers(List<Answer> answers) {

		List<BaseEntity> targets = new ArrayList<>();

		// sort answers into target BaseEntitys
		Map<String, List<Answer>> answersPerTargetCodeMap = answers.stream()
				.collect(Collectors.groupingBy(Answer::getTargetCode));

		for (String targetCode : answersPerTargetCodeMap.keySet()) {

			// fetch target and target DEF
			BaseEntity target = beUtils.getBaseEntity(targetCode);
			BaseEntity defBE = defUtils.getDEF(target);

			// filter Non-valid answers using def
			List<Answer> group = answersPerTargetCodeMap.get(targetCode);
			List<Answer> validAnswers = group.stream()
					.filter(item -> defUtils.answerValidForDEF(defBE, item))
					.collect(Collectors.toList());

			// update target using valid answers
			for (Answer answer : validAnswers) {
				Attribute attribute = getAttribute(answer.getAttributeCode());
				answer.setAttribute(attribute);
				try {
					target.addAnswer(answer);
				} catch (BadDataException e) {
					log.error(e);
				}
			}

			// update target in the cache and DB
			beUtils.updateBaseEntity(target);
			targets.add(target);
		}

		return targets;
	}

	/**
	 * Delete a currently scheduled message via shleemy.
	 *
	 * @param code the code of the schedule message to delete
	 */
	public void deleteSchedule(String code) {

		String uri = GennySettings.shleemyServiceUrl() + "/api/schedule/code/" + code;
		HttpUtils.delete(uri, userToken);
	}

	/**
	 * Generate Question group for a baseEntity
	 *
	 * @param baseEntity the baseEntity to create for
	 * @return Ask
	 */
	public Ask generateAskGroupUsingBaseEntity(BaseEntity baseEntity) {

		// grab def entity
		BaseEntity defBE = defUtils.getDEF(baseEntity);

		// create GRP ask
		Attribute questionAttribute = getAttribute("QQQ_QUESTION_GROUP");
		// Question question = new Question("QUE_EDIT_GRP", "Edit " +
		// baseEntity.getCode() + " : " + baseEntity.getName(),
		Question question = new Question("QUE_BASEENTITY_GRP",
				"Edit " + baseEntity.getCode() + " : " + baseEntity.getName(),
				questionAttribute);
		Ask ask = new Ask(question, userToken.getUserCode(), baseEntity.getCode());

		List<Ask> childAsks = new ArrayList<>();
		QDataBaseEntityMessage entityMessage = new QDataBaseEntityMessage();
		entityMessage.setToken(userToken.getToken());
		entityMessage.setReplace(true);

		// create a child ask for every valid atribute
		baseEntity.getBaseEntityAttributes().stream()
				.filter(ea -> defBE.containsEntityAttribute("ATT_" + ea.getAttributeCode()))
				.forEach((ea) -> {

					String questionCode = "QUE_"
							+ StringUtils.removeStart(StringUtils.removeStart(ea.getAttributeCode(), "PRI_"), "LNK_");

					Question childQues = new Question(questionCode, ea.getAttribute().getName(), ea.getAttribute());
					Ask childAsk = new Ask(childQues, userToken.getUserCode(), baseEntity.getCode());

					childAsks.add(childAsk);

					if (ea.getAttributeCode().startsWith("LNK_")) {
						if (ea.getValueString() != null) {

							String[] codes = beUtils.cleanUpAttributeValue(ea.getValueString()).split(",");

							for (String code : codes) {
								try {
									BaseEntity link = beUtils.getBaseEntityByCode(code);
									entityMessage.add(link);
								} catch (Exception ex) {
									log.error(ex);
								}
							}
						}
					}

					if (defBE.containsEntityAttribute("SER_" + ea.getAttributeCode())) {
						searchUtils.performDropdownSearch(childAsk);
					}
				});

		// set child asks
		ask.setChildAsks(childAsks.toArray(new Ask[childAsks.size()]));

		// KafkaUtils.writeMsg("webdata", entityMessage);

		return ask;
	}

	/**
	 * Update the status of the disabled field for an Ask on the web.
	 *
	 * @param ask      the ask to update
	 * @param disabled the disabled status to set
	 */
	public void updateAskDisabled(Ask ask, Boolean disabled) {

		ask.setDisabled(disabled);

		QDataAskMessage askMsg = new QDataAskMessage(ask);
		askMsg.setToken(userToken.getToken());
		askMsg.setReplace(true);
		String json = jsonb.toJson(askMsg);
		KafkaUtils.writeMsg("webcmds", json);
	}
/**
         * Check the uniqueness of an answer
         * 
         * @param target        The target entity
         * @param definition    The definition entity
         * @param attributeCode The code of the attribute
         * @param value The value to check
         * @return is duplicate bool
         */
        public Boolean isDuplicate(BaseEntity target, BaseEntity definition, String attributeCode, String value) {

                if (StringUtils.isBlank(value)) {
                        return false;
                }

                // Check if attribute code exists as a UNQ for the DEF
                String uniqueCode = String.format("UNQ_%s", attributeCode);
                List<String> unqs = beUtils.getBaseEntityCodeArrayFromLinkAttribute(definition, uniqueCode);

                if (unqs == null || unqs.isEmpty()) {
                        log.infof("UNQ_%s Not present!", attributeCode);
                        return false;
                }

                BaseEntity originalTarget = beUtils.getBaseEntityByCode(definition.getValueAsString("PRI_PREFIX") + target.getCode().substring(3));
                log.info("Target: " + target.getCode() + ", Definition: " + definition.getCode());
                log.info("Attribute " + uniqueCode + " must satisfy " + unqs.toString());

                String prefix = definition.getValueAsString("PRI_PREFIX");
                log.info("Looking for duplicates using prefix: " + prefix + " and realm " + target.getRealm());
                SearchEntity searchEntity = new SearchEntity("SBE_COUNT_UNIQUE_PAIRS", "Count Unique Pairs")
                                .addFilter("PRI_CODE", SearchEntity.StringFilter.LIKE, prefix + "_%")
                                .setPageStart(0)
                                .setPageSize(1);

                searchEntity.setRealm(target.getRealm());

                log.info("Checking all the existing target data");
                for (EntityAttribute ea : target.getBaseEntityAttributes()) {
                        log.info(target.getCode() + ":" + ea.getAttributeCode() + ": " + ea.getAsString());
                }

                log.infof("Looping through %s unique attribute", unqs.size());

                for (String unique : unqs) {

                        if (!unique.equals(attributeCode)) {
                                if (!target.containsEntityAttribute(unique)) {
                                        value = originalTarget.getValueAsString(unique);
                                } else {
                                        value = target.getValueAsString(unique);
                                }
                        }

                        if (value.contains("[") && value.contains("]"))
                                value = beUtils.cleanUpAttributeValue(value);
                        log.info("Adding unique filter: " + unique + " like " + value);
                        searchEntity.addFilter(unique, SearchEntity.StringFilter.LIKE, "%" + value + "%");
                }

                searchEntity.setRealm(userToken.getProductCode());

                Long count = searchUtils.countBaseEntitys(searchEntity);
                log.infof("Found %s entities", count);
                if (count == 0)
                        return false;

                // not duplicate if it is targets code
                List<String> codes = searchUtils.searchBaseEntityCodes(searchEntity);
                if (codes != null && !codes.isEmpty() && codes.get(0).equals(target.getCode()))
                        return false;

                // if duplicate found then send back the baseentity with the conflicting
                // attribute and feedback message to display
                // QUE target code needs to be set
                BaseEntity errorBE = new BaseEntity(target.getCode(), definition.getCode());
                Optional<EntityAttribute> optEa = target.findEntityAttribute(attributeCode);
                if (optEa.isPresent()) {
                        String feedback = "Error: This value already exists and must be unique.";
                        EntityAttribute ea = optEa.get();
                        ea.setFeedback(feedback);
                        errorBE.addAttribute(ea);
                        log.info("Added " + ea);

                        QDataBaseEntityMessage msg = new QDataBaseEntityMessage(errorBE);
                        msg.setToken(userToken.getToken());
                        msg.setTag("Duplicate Error");
                        msg.setReplace(false);
                        msg.setMessage(feedback);
                        KafkaUtils.writeMsg("webcmds", msg);

                        log.debug("Sent duplicate error message to frontend for " + target.getCode());
                }

                return true;
        }
}
