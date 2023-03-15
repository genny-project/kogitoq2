package life.genny.bootq.utils;

import life.genny.qwandaq.Question;
import life.genny.qwandaq.QuestionQuestion;
import life.genny.qwandaq.attribute.Attribute;
import life.genny.qwandaq.attribute.EntityAttribute;
import life.genny.qwandaq.datatype.DataType;
import life.genny.qwandaq.entity.BaseEntity;
import life.genny.qwandaq.exception.runtime.BadDataException;
import life.genny.qwandaq.exception.runtime.ItemNotFoundException;
import life.genny.qwandaq.managers.CacheManager;
import life.genny.qwandaq.utils.AttributeUtils;
import life.genny.qwandaq.utils.BaseEntityUtils;
import life.genny.qwandaq.utils.EntityAttributeUtils;
import life.genny.qwandaq.utils.QuestionUtils;
import life.genny.qwandaq.validation.Validation;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.json.bind.Jsonb;
import javax.json.bind.JsonbBuilder;
import org.apache.commons.lang3.StringUtils;
import org.jboss.logging.Logger;

@ApplicationScoped
public class GoogleSheetBuilder {

    private static final String WEIGHT = "weight";
    private static final String REGEX_1 = "^\"|\"$";
    private static final String REGEX_2 = "^\"|\"$|_|-";
    private static final String PRIVACY = "privacy";
    private static final String CONFIRMATION = "confirmation";
    private static final String VALUEINTEGER = "valueinteger";
    private static final String VALUEBOOLEAN = "valueboolean";
    private static final String VALUEDOUBLE = "valuedouble";
    private static final String VALUESTRING = "valuestring";
    private static final String VALUEBDATETIME = "valuedatetime";
    private static final String VALUEDATE = "valuedate";
    private static final String VALUETIME = "valuetime";
    private static final String VALUELONG = "valuelong";

    public static final String MANDATORY = "mandatory";
    public static final String READONLY = "readonly";

    Jsonb jsonb = JsonbBuilder.create();

	@Inject
	Logger log;

	@Inject
	CacheManager cm;

	@Inject
	AttributeUtils attributeUtils;

	@Inject
	EntityAttributeUtils beaUtils;

	@Inject
	BaseEntityUtils beUtils;

	@Inject
	QuestionUtils questionUtils;

    public GoogleSheetBuilder() { }

    /**
	 * Convert a String to a Boolean
	 *
     * @param booleanString
     * @return Boolean or null.
     */
    public static Boolean toBoolean(final String booleanString) {
        if (StringUtils.isBlank(booleanString)) {
            return null;
        }
        return "TRUE".equalsIgnoreCase(booleanString);
    }

    /**
	 * Convert a String to a Double
	 *
     * @param doubleString
     * @return Double or null.
     */
    public static Double toDouble(final String doubleString) {
        if (StringUtils.isBlank(doubleString)) {
            return null;
        }
        return Double.parseDouble(doubleString);
    }

    /**
	 * Convert a String to a Integer
	 *
     * @param intString
     * @return Integer or null.
     */
    public static Integer toInt(final String intString) {
        if (StringUtils.isBlank(intString)) {
			return null;
		}
        return Integer.valueOf(intString);
	}

    /**
	 * Convert a String to a Long
	 *
     * @param longString
     * @return Double or null.
     */
    public static Long toLong(final String longString) {
        if (StringUtils.isBlank(longString)) {
			return null;
		}
        return Long.valueOf(longString);
	}

    /**
	 * Build a Validation object from a row.
	 *
     * @param row The row from the sheets
     * @param realmName The realm
     * @return A Validation object
     */
    public Validation buildValidation(Map<String, String> row, String realmName) {

		String code = row.get("code");
		Validation validation;
		try {
			validation = attributeUtils.getValidation(realmName, code);
		} catch (ItemNotFoundException e) {
			// create new validation if not found
			validation = new Validation();
			validation.setCode(code);
		}

		String name = row.get("name");
		String regex = row.get("regex");
		String error = row.get("errormessage");
		validation.setName(name);
		validation.setRegex(regex);
		validation.setErrormsg(error);
		
		// handle the group codes
		String group_codes = row.get("groupcodes");
		List<String> groups = StringUtils.isBlank(group_codes) ? new ArrayList<>() : Arrays.asList(group_codes.replaceAll(" ", "").split(","));
		validation.setSelectionBaseEntityGroupList(groups);

		validation.setRealm(realmName);
        return validation;
    }

    /**
	 * Build a DataType object from a row.
	 *
     * @param row The row from the sheets
     * @param realmName The realm
     * @return A DataType object
     */
	public DataType buildDataType(Map<String, String> row, String realmName) {

		String code = row.get("code");
		DataType dataType;
		try {
			dataType = attributeUtils.getDataType(realmName, code, false);
		} catch (ItemNotFoundException e) {
			// create new datatype if not found
			dataType = new DataType();
			dataType.setDttCode(code);
		}

		dataType.setClassName(row.get("classname"));
		dataType.setInputmask(row.get("inputmask"));
		dataType.setComponent(row.get("component"));
		dataType.setTypeName(row.get("name"));
		dataType.setValidationCodes(row.get("validations"));
		dataType.setRealm(realmName);
		return dataType;
	}

    /**
	 * Build a Attribute object from a row.
	 *
     * @param row The row from the sheets
     * @param realmName The realm
     * @return An Attribute object
     */
    public Attribute buildAttribute(Map<String, String> row, String realmName, Long id) {
		if (id == null) {
			id = cm.getMaxAttributeId();
		}

        String code = row.get("code");
        String name = row.get("name");

		Attribute attribute;
		try {
			attribute = attributeUtils.getAttribute(realmName, code, false);
			log.trace("Found Attribute: " + attribute.getCode());
		} catch(ItemNotFoundException e) {
			log.trace("[!] Creating new Attribute: " + code);
			// create new attribute if not found
			attribute = new Attribute();
			attribute.setCode(code);
			attribute.setName(name);
			attribute.setId(id+1);
		}
		Boolean privacy = toBoolean(row.get("privacy"));
		
		attribute.setDttCode(row.get("datatype"));
        attribute.setDefaultPrivacyFlag(privacy == null ? false : privacy);
        attribute.setDescription(row.get("description"));
        attribute.setHelp(row.get("help"));
        attribute.setPlaceholder(row.get("placeholder"));
        attribute.setDefaultValue(row.get("defaultvalue"));
        attribute.setIcon(row.get("icon"));
        attribute.setRealm(realmName);
        return attribute;
    }

    /**
	 * Build a BaseEntity object from a row.
	 *
     * @param row THe row from the sheets
     * @param realmName The realm
     * @return A BaseEntity object
     */
    public BaseEntity buildBaseEntity(Map<String, String> row, String realmName, Long id) {

        String code = row.get("code");
		BaseEntity baseEntity;
		try {
			baseEntity = beUtils.getBaseEntity(realmName, code, false);
		} catch (ItemNotFoundException e) {
			baseEntity = new BaseEntity();
			baseEntity.setCode(code);
			baseEntity.setId(id);
		}
        String name = row.get("name");
		baseEntity.setName(name);
        baseEntity.setRealm(realmName);
        return baseEntity;
    }

    /**
	 * Build a EntityAttribute object from a row.
	 *
     * @param row THe row from the sheets
     * @param realmName The realm
     * @return A EntityAttribute object
     */
    public EntityAttribute buildEntityAttribute(Map<String, String> row, String realmName) {
		return buildEntityAttribute(row, realmName, row.get("attributecode"));
	}

    /**
	 * Build a EntityAttribute object from a row.
	 *
     * @param row THe row from the sheets
     * @param realmName The realm
     * @param attributeCode The attributeCode to set
     * @return A EntityAttribute object
     */
    public EntityAttribute buildEntityAttribute(Map<String, String> row, String realmName, String attributeCode) {

		EntityAttribute entityAttribute = new EntityAttribute();
		String baseEntityCode = row.get("baseentitycode");

		BaseEntity baseEntity;
		Attribute attribute;

		try {
			baseEntity = beUtils.getBaseEntity(realmName, baseEntityCode, false);
		} catch(ItemNotFoundException e) {
			throw new BadDataException("Bad or missing BaseEntity: " + baseEntityCode + " in product " + realmName, e);
		}

		try {
			attribute = attributeUtils.getAttribute(realmName, attributeCode, false);
		} catch(ItemNotFoundException e) {
			throw new BadDataException("Bad or missing Attribute: " + attributeCode + " in product " + realmName, e);
		}

		entityAttribute.setBaseEntityCode(baseEntityCode);
		entityAttribute.setBaseEntityId(baseEntity.getId());
		entityAttribute.setAttribute(attribute);
		entityAttribute.setRealm(realmName);
        
        String valueString = row.get(VALUESTRING);
        Integer valueInt = toInt(row.get(VALUEINTEGER));
        Long valueLong = toLong(row.get(VALUELONG));
        Double valueDouble = toDouble(row.get(VALUEDOUBLE));
        Boolean valueBoolean = toBoolean(row.get(VALUEBOOLEAN));

        Double weight = toDouble(row.get(WEIGHT));

        Boolean privacy = toBoolean(row.get(PRIVACY));
        Boolean confirmation = toBoolean(row.get(CONFIRMATION));

		entityAttribute.setValueString(valueString);
		entityAttribute.setValueInteger(valueInt);
		entityAttribute.setValueLong(valueLong);
		entityAttribute.setValueDouble(valueDouble);
		entityAttribute.setValueBoolean(valueBoolean);

		entityAttribute.setWeight(weight);
		entityAttribute.setPrivacyFlag(privacy == null ? false : privacy);
		entityAttribute.setConfirmationFlag(confirmation == null ? false : confirmation);

        return entityAttribute;
    }

    /**
	 * Build a Question object from a row.
	 *
     * @param row The row from the sheet
     * @param realmName the realm
     * @return A Question
     */
    public Question buildQuestion(Map<String, String> row, String realmName) {

        String code = row.get("code");
		Question question = questionUtils.getQuestionFromQuestionCode(realmName, code);
		if (question == null) {
			question = new Question();
			question.setCode(code);
		}
        String attributeCode = row.get("attributecode");

		Attribute attribute = attributeUtils.getAttribute(realmName, attributeCode, false);
		if (attribute == null) {
			throw new ItemNotFoundException(realmName, attributeCode);
		}

        String name = row.get("name");
        String html = row.get("html");
        String placeholder = row.get("placeholder");
        Boolean readonly = toBoolean(row.get(READONLY));
        Boolean mandatory = toBoolean(row.get(MANDATORY));
        String icon = row.get("icon");

		question.setName(name);
		question.setAttribute(attribute);
        question.setHtml(html);
		question.setPlaceholder(placeholder);
        question.setReadonly(readonly == null ? false : readonly);
        question.setMandatory(mandatory == null ? false : mandatory);
        question.setIcon(icon);
        question.setRealm(realmName);

        return question;
    }

    /**
	 * Build a QuestionQuestion object from a row.
	 *
     * @param row THe row from the sheets
     * @param realmName The realm
     * @return A QuestionQuestion object
     */
    public QuestionQuestion buildQuestionQuestion(Map<String, String> row, String realmName) {

        String parentCode = row.get("parentcode");
		Question source = questionUtils.getQuestionFromQuestionCode(realmName, parentCode);
		if (source == null) {
			throw new ItemNotFoundException(realmName, parentCode);
		}
        String targetCode = row.get("targetcode");
		Question target = questionUtils.getQuestionFromQuestionCode(realmName, targetCode);
		if (target == null) {
			throw new ItemNotFoundException(realmName, parentCode);
		}

        Double weight = toDouble(row.get(WEIGHT));
        Boolean mandatory = toBoolean(row.get(MANDATORY));
        Boolean readonly = toBoolean(row.get(READONLY));

        String icon = row.get("icon");
        Boolean disabled = toBoolean(row.get("disabled"));
        Boolean hidden = toBoolean(row.get("hidden"));

		QuestionQuestion questionQuestion = new QuestionQuestion();
		questionQuestion.setParentCode(parentCode);
		questionQuestion.setChildCode(targetCode);
		questionQuestion.setWeight(weight);
        questionQuestion.setReadonly(readonly == null ? false : readonly);
        questionQuestion.setMandatory(mandatory == null ? false : mandatory);
		questionQuestion.setDisabled(disabled == null ? false : disabled);
		questionQuestion.setHidden(hidden == null ? false : hidden);
		questionQuestion.setIcon(icon);
		questionQuestion.setRealm(realmName);
		questionQuestion.setParentId(source.getId());
		return questionQuestion;
    }

}
