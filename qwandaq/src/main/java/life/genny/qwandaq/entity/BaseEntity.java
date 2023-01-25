/*
 * (C) Copyright 2017 GADA Technology (http://www.outcome-hub.com/) and others.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 *
 * Contributors: Adam Crow Byron Aguirre
 */

package life.genny.qwandaq.entity;

import java.util.*;
import java.util.stream.Collectors;
import javax.json.bind.annotation.JsonbTransient;
import javax.persistence.Transient;

import org.infinispan.protostream.annotations.ProtoFactory;
import org.jboss.logging.Logger;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;

import io.quarkus.runtime.annotations.RegisterForReflection;

import life.genny.qwandaq.Answer;
import life.genny.qwandaq.AnswerLink;
import life.genny.qwandaq.CodedEntity;
import life.genny.qwandaq.CoreEntityPersistable;
import life.genny.qwandaq.attribute.Attribute;
import life.genny.qwandaq.attribute.EntityAttribute;
import life.genny.qwandaq.constants.Prefix;
import life.genny.qwandaq.datatype.capability.core.Capability;
import life.genny.qwandaq.exception.runtime.BadDataException;
import life.genny.qwandaq.intf.ICapabilityFilterable;
import life.genny.qwandaq.serialization.CoreEntitySerializable;

/**
 * BaseEntity represents a base entity that contains many attributes. It is the
 * base parent for many Qwanda classes and serves to establish Hibernate
 * compatibility and datetime stamping. BaseEntity objects may be scored against
 * each other. BaseEntity objects may not have a deterministic code Examples of
 * derivative entities may be Person, Company, Event, Product, TradeService.
 * This attribute information includes:
 * <ul>
 * <li>The List of attributes
 * </ul>
 *
 * 
 * 
 * @author Adam Crow
 * @author Byron Aguirre
 * @version %I%, %G%
 * @since 1.0
 */
@RegisterForReflection
public class BaseEntity extends CodedEntity implements ICapabilityFilterable, CoreEntityPersistable, BaseEntityIntf {

	@Transient
	private static final long serialVersionUID = 1L;

	private static final Logger log = Logger.getLogger(BaseEntity.class);

	public static final String PRI_NAME = "PRI_NAME";
	public static final String PRI_IMAGE_URL = "PRI_IMAGE_URL";
	public static final String PRI_PHONE = "PRI_PHONE";
	public static final String PRI_ADDRESS_FULL = "PRI_ADDRESS_FULL";
	public static final String PRI_EMAIL = "PRI_EMAIL";

	private Map<String, EntityAttribute> baseEntityAttributes = new HashMap<>(0);

	private Set<EntityEntity> links = new LinkedHashSet<>();

	@Transient
	@JsonbTransient
	private Set<EntityQuestion> questions = new HashSet<EntityQuestion>(0);

	private transient Set<AnswerLink> answers = new HashSet<AnswerLink>(0);

	private Set<Capability> capabilityRequirements;

	/**
	 * Constructor.
	 */
	public BaseEntity() {
		super();
	}

	/**
	 * Constructor.
	 * 
	 * @param code the unique code of the core entity
	 * @param name the summary name of the core entity
	 */
	@ProtoFactory
	public BaseEntity(final String code, final String name) {
		super(code, name);
	}

	@JsonbTransient
	@JsonIgnore
	public Set<Capability> getCapabilityRequirements() {
		return this.capabilityRequirements;
	}

	@Override
	@JsonbTransient
	@JsonIgnore
	public void setCapabilityRequirements(Set<Capability> requirements) {
		this.capabilityRequirements = requirements;
	}

	public Set<AnswerLink> getAnswers() {
		return answers;
	}

	public void setAnswers(final Set<AnswerLink> answers) {
		this.answers = answers;
	}

	public void setAnswers(final List<AnswerLink> answers) {
		this.answers.addAll(answers);
	}

	public Collection<EntityAttribute> getBaseEntityAttributes() {
		return baseEntityAttributes.values();
	}

	@JsonbTransient
	public Map<String, EntityAttribute> getBaseEntityAttributesMap() {
		return baseEntityAttributes;
	}

	@JsonbTransient
	public void setBaseEntityAttributes(final Map<String, EntityAttribute> baseEntityAttributesMap) {
		this.baseEntityAttributes = baseEntityAttributesMap;
	}

	@JsonbTransient
	public void setBaseEntityAttributes(final Set<EntityAttribute> baseEntityAttributes) {
		baseEntityAttributes.forEach(bea -> this.baseEntityAttributes.put(bea.getAttributeCode(), bea));
	}

	@JsonbTransient
	public void setBaseEntityAttributes(final Collection<EntityAttribute> baseEntityAttributes) {
		baseEntityAttributes.forEach(bea -> this.baseEntityAttributes.put(bea.getAttributeCode(), bea));
	}

	@JsonInclude(JsonInclude.Include.NON_NULL)
	public Set<EntityEntity> getLinks() {
		return links;
	}

	public void setLinks(final Set<EntityEntity> links) {
		this.links = links;
	}

	public void setLinks(final List<EntityEntity> links) {
		this.links.addAll(links);
	}

	@JsonInclude(JsonInclude.Include.NON_NULL)
	public Set<EntityQuestion> getQuestions() {
		return this.questions;
	}

	public void setQuestions(final Set<EntityQuestion> questions) {
		this.questions = questions;
	}

	@JsonbTransient
	public void setQuestions(final List<EntityQuestion> questions) {
		this.questions.addAll(questions);
	}

	/**
	 * containsEntityAttribute This checks if an attribute exists in the baseEntity.
	 * 
	 * @param attributeCode the attributeCode to check
	 * @return boolean
	 */
	public boolean containsEntityAttribute(final String attributeCode) {
		return getBaseEntityAttributesMap().containsKey(attributeCode);
	}

	/**
	 * containsLink This checks if an attribute link code is linked to the
	 * baseEntity.
	 * 
	 * @param linkAttributeCode the linkAttributeCode to check
	 * @return boolean
	 */
	public boolean containsLink(final String linkAttributeCode) {
		boolean ret = false;
		// Check if this code exists in the baseEntityAttributes
		if (getLinks().parallelStream().anyMatch(ti -> ti.getAttribute().getCode().equals(linkAttributeCode))) {
			ret = true;
		}
		return ret;
	}

	/**
	 * containsTarget This checks if another baseEntity is linked to the baseEntity.
	 * 
	 * @param targetCode        the targetCode to check
	 * @param linkAttributeCode the linkAttributeCode to check
	 * @return boolean
	 */
	public boolean containsTarget(final String targetCode, final String linkAttributeCode) {
		boolean ret = false;
		// Check if this code exists in the baseEntityAttributes
		if (getLinks().parallelStream().anyMatch(ti -> (ti.getLink().getAttributeCode().equals(linkAttributeCode)
				&& (ti.getLink().getTargetCode().equals(targetCode))))) {
			ret = true;
		}
		return ret;
	}

	/**
	 * findEntityAttribute This returns an attributeEntity if it exists in the
	 * baseEntity.
	 * 
	 * @param attributeCode the attributeCode to find with
	 * @return Optional
	 */
	@Deprecated
	public Optional<EntityAttribute> findEntityAttribute(final String attributeCode) {
		return Optional.ofNullable(this.baseEntityAttributes.get(attributeCode));
	}

	/**
	 * findEntityAttribute This returns an attributeEntity if it exists in the
	 * baseEntity. Could be more efficient in retrival (ACC: test)
	 * 
	 * @param attributePrefix the attributePrefix to find with
	 * @return EntityAttribute
	 */
	public List<EntityAttribute> findPrefixEntityAttributes(final String attributePrefix) {
		return getBaseEntityAttributes().stream()
				.filter(x -> (x.getAttributeCode().startsWith(attributePrefix)))
				.collect(Collectors.toList());
	}

	/**
	 * findEntityAttributes This returns attributeEntitys if it exists in the
	 * baseEntity. Could be more efficient in retrival (ACC: test)
	 * 
	 * @param attribute the attribute to find
	 * @return EntityAttribute
	 */
	public Optional<EntityAttribute> findEntityAttribute(final Attribute attribute) {
		return Optional.ofNullable(getBaseEntityAttributesMap().get(attribute.getCode()));
	}

	/**
	 * addAttribute This adds an attribute with default weight of 0.0 to the
	 * baseEntity. It auto creates the EntityAttribute object. For efficiency we
	 * assume the attribute does not already exist
	 * 
	 * @param ea the ea to add
	 * @return EntityAttribute
	 * @throws BadDataException if the attribute could not be added
	 */
	public EntityAttribute addAttribute(final EntityAttribute ea) throws BadDataException {
		if (ea == null) {
			throw new BadDataException("missing Attribute");
		}
		return addAttribute(ea.getAttribute(), ea.getWeight(), ea.getValue());
	}

	/**
	 * addAttribute This adds an attribute and associated weight to the baseEntity.
	 * It auto creates the EntityAttribute object. For efficiency we assume the
	 * attribute does not already exist
	 * 
	 * @param attribute tha Attribute to add
	 * @throws BadDataException if attribute could not be added
	 * @return EntityAttribute
	 */
	public EntityAttribute addAttribute(final Attribute attribute) throws BadDataException {
		return addAttribute(attribute, 1.0);
	}

	/**
	 * addAttribute This adds an attribute and associated weight to the baseEntity.
	 * It auto creates the EntityAttribute object. For efficiency we assume the
	 * attribute does not already exist
	 * 
	 * @param attribute tha Attribute to add
	 * @param weight    the weight
	 * @throws BadDataException if attribute could not be added
	 * @return EntityAttribute
	 */
	public EntityAttribute addAttribute(final Attribute attribute, final Double weight) throws BadDataException {
		return addAttribute(attribute, weight, null);
	}

	/**
	 * addAttribute This adds an attribute and associated weight to the baseEntity.
	 * It auto creates the EntityAttribute object. For efficiency we assume the
	 * attribute does not already exist
	 * 
	 * @param attribute tha Attribute to add
	 * @param weight    the weight
	 * @param value     of type String, LocalDateTime, Long, Integer, Boolean
	 * @throws BadDataException if attribute could not be added
	 * @return EntityAttribute
	 */
	public EntityAttribute addAttribute(final Attribute attribute, final Double weight, final Object value)
			throws BadDataException {

		if (attribute == null)
			throw new BadDataException("missing Attribute");
		if (weight == null)
			throw new BadDataException("missing weight");

		final EntityAttribute entityAttribute = new EntityAttribute(this, attribute, weight, value);
		entityAttribute.setRealm(getRealm());
		EntityAttribute existing = this.baseEntityAttributes.get(attribute.getCode());
		if (existing != null) {
			if (value != null)
				existing.setValue(value);
			existing.setWeight(weight);
		} else {
			this.baseEntityAttributes.put(attribute.getCode(), entityAttribute);
		}
		return entityAttribute;
	}

	/**
	 * addAttributeOmitCheck This adds an attribute and associated weight to the
	 * baseEntity. This method will NOT check and update any existing attributes.
	 * Use with Caution.
	 * 
	 * @param attribute tha Attribute to add the omit check to
	 * @param weight    the weight of the omit check
	 * @param value     of type String, LocalDateTime, Long, Integer, Boolean
	 * @throws BadDataException if omit check could not be added
	 * @return EntityAttribute
	 */
	public EntityAttribute addAttributeOmitCheck(final Attribute attribute, final Double weight, final Object value)
			throws BadDataException {
		if (attribute == null)
			throw new BadDataException("missing Attribute");
		if (weight == null)
			throw new BadDataException("missing weight");

		final EntityAttribute entityAttribute = new EntityAttribute(this, attribute, weight, value);
		entityAttribute.setRealm(getRealm());
		entityAttribute.setBaseEntityCode(getCode());
		entityAttribute.setAttribute(attribute);
		this.baseEntityAttributes.put(attribute.getCode(), entityAttribute);

		return entityAttribute;
	}

	/**
	 * removeAttribute This removes an attribute and associated weight from the
	 * baseEntity. For efficiency we assume the attribute exists
	 * 
	 * @param attributeCode the code of the Attribute to remove
	 * @return Boolean
	 */
	public Boolean removeAttribute(final String attributeCode) {
		return this.getBaseEntityAttributesMap().remove(attributeCode) != null ? true : false;
	}

	/**
	 * addTarget This links this baseEntity to a target BaseEntity and associated
	 * weight,value to the baseEntity. It auto creates the EntityEntity object and
	 * sets itself to be the source. For efficiency we assume the link does not
	 * already exist
	 * 
	 * @param target        the target to add
	 * @param linkAttribute the attribute link
	 * @param weight        the weight of the target
	 * @return EntityEntity
	 * @throws BadDataException if the target could not be added
	 */
	public EntityEntity addTarget(final BaseEntity target, final Attribute linkAttribute, final Double weight)
			throws BadDataException {
		return addTarget(target, linkAttribute, weight, null);
	}

	/**
	 * addTarget This links this baseEntity to a target BaseEntity and associated
	 * weight,value to the baseEntity. It auto creates the EntityEntity object and
	 * sets itself to be the source. For efficiency we assume the link does not
	 * already exist
	 * 
	 * @param target        the target to add
	 * @param linkAttribute the attribute link
	 * @param weight        the weight of the target
	 * @param value         the value of the target
	 * @return EntityEntity
	 * @throws BadDataException if the target could not be added
	 */
	public EntityEntity addTarget(final BaseEntity target, final Attribute linkAttribute, final Double weight,
			final Object value) throws BadDataException {
		if (target == null)
			throw new BadDataException("missing Target Entity");
		if (linkAttribute == null)
			throw new BadDataException("missing Link Attribute");
		if (weight == null)
			throw new BadDataException("missing weight");

		final EntityEntity entityEntity = new EntityEntity(getRealm(), getCode(), target.getCode(), linkAttribute, value, weight);
		getLinks().add(entityEntity);
		return entityEntity;
	}

	/**
	 * addAnswer This links this baseEntity to a target BaseEntity and associated
	 * Answer. It auto creates the AnswerLink object and sets itself to be the
	 * source and assumes itself to be the target. For efficiency we assume the link
	 * does not already exist and weight = 0
	 * 
	 * @param answer the answer to add
	 * @return AnswerLink
	 * @throws BadDataException if answer could not be added
	 */
	public AnswerLink addAnswer(final Answer answer) throws BadDataException {
		return addAnswer(this, answer, 0.0);
	}

	/**
	 * addAnswer This links this baseEntity to a target BaseEntity and associated
	 * Answer. It auto creates the AnswerLink object and sets itself to be the
	 * source and assumes itself to be the target. For efficiency we assume the link
	 * does not already exist
	 * 
	 * @param answer the answer to add
	 * @param weight the weight of the answer
	 * @return AnswerLink
	 * @throws BadDataException if answer could not be added
	 */
	public AnswerLink addAnswer(final Answer answer, final Double weight) throws BadDataException {
		return addAnswer(this, answer, weight);
	}

	/**
	 * addAnswer This links this baseEntity to a target BaseEntity and associated
	 * Answer. It auto creates the AnswerLink object and sets itself to be the
	 * source. For efficiency we assume the link does not already exist
	 * 
	 * @param source the source entity
	 * @param answer the answer to add
	 * @param weight the weight of the answer
	 * @return AnswerLink
	 * @throws BadDataException if answer could not be added
	 */
	public AnswerLink addAnswer(final BaseEntity source, final Answer answer, final Double weight)
			throws BadDataException {
		if (source == null)
			throw new BadDataException("missing Target Entity");
		if (answer == null)
			throw new BadDataException("missing Answer");
		if (weight == null)
			throw new BadDataException("missing weight");

		final AnswerLink answerLink = new AnswerLink(source.toHBaseEntity(), this.toHBaseEntity(), answer, weight);
		if (answer.getAskId() != null) {
			answerLink.setAskId(answer.getAskId());
		}

		// Update the EntityAttribute
		Optional<EntityAttribute> ea = findEntityAttribute(answer.getAttributeCode());
		if (ea.isPresent()) {
			// modify
			ea.get().setValue(answerLink.getValue());
			ea.get().setInferred(answer.getInferred());
			ea.get().setWeight(answer.getWeight());
			ea.get().setRealm(getRealm());
			ea.get().setBaseEntityCode(getCode());
		} else {
			Attribute attribute = new Attribute();
			attribute.setCode(answer.getAttributeCode());
			EntityAttribute newEA = new EntityAttribute(this, attribute, weight, answerLink.getValue());
			newEA.setRealm(getRealm());
			newEA.setBaseEntityCode(getCode());
			newEA.setAttributeCode(answerLink.getAttributeCode());
			newEA.setInferred(answerLink.getInferred());
			this.baseEntityAttributes.put(answerLink.getAttributeCode(), newEA);
		}

		return answerLink;
	}

	/**
	 * @param <T>       The Type to return
	 * @param attribute
	 * @return T
	 */
	@JsonIgnore
	@JsonbTransient
	@Transient
	private <T> T getValue(final Attribute attribute) {
		EntityAttribute entityAttribute = this.baseEntityAttributes.get(attribute.getCode());
		if (entityAttribute != null) {
			return getValue(entityAttribute);
		}
		return null;
	}

	/**
	 * @param <T> The Type to return
	 * @param ea  the ea to get
	 * @return T
	 */
	@JsonIgnore
	@JsonbTransient
	@Transient
	private <T> T getValue(final EntityAttribute ea) {
		return ea.getValue();

	}

	/**
	 * @param <T>           The Type to return
	 * @param attributeCode the attributeCode to get with
	 * @return Optional
	 */
	@JsonIgnore
	@JsonbTransient
	@Transient
	public <T> Optional<T> getValue(final String attributeCode) {

		Optional<EntityAttribute> ea = this.findEntityAttribute(attributeCode);

		Optional<T> result = Optional.empty();
		if (ea.isPresent()) {
			if (ea.get() != null) {
				if (ea.get().getValue() != null) {
					result = Optional.of(ea.get().getValue());
				}
			}
		}
		return result;
	}

	/**
	 * @param <T>           the Type to return
	 * @param attributeCode the code of the attribute value to get
	 * @return Optional
	 */
	@JsonIgnore
	@JsonbTransient
	@Transient
	public <T> Optional<T> getLoopValue(final String attributeCode) {

		Optional<EntityAttribute> ea = this.findEntityAttribute(attributeCode);

		Optional<T> result = Optional.empty();
		if (ea.isPresent()) {
			result = Optional.of(ea.get().getLoopValue());
		}
		return result;
	}

	/**
	 * @param attributeCode the code of the attribute value to get
	 * @return String
	 */
	@JsonIgnore
	@JsonbTransient
	@Transient
	public String getValueAsString(final String attributeCode) {

		Optional<EntityAttribute> ea = this.findEntityAttribute(attributeCode);
		String result = null;
		if (ea.isPresent()) {
			if (ea.get() != null) {
				if (ea.get().getValue() != null) {
					result = ea.get().getAsString();
				}
			}
		}
		return result;
	}

	/**
	 * Get the value
	 *
	 * @param attributeCode the attribute code
	 * @param defaultValue  the default value
	 * @param <T>           The Type to return
	 * @return T
	 */
	@JsonIgnore
	@JsonbTransient
	@Transient
	public <T> T getValue(final String attributeCode, T defaultValue) {

		Optional<T> result = getValue(attributeCode);
		if (result.isPresent()) {
			if (!result.equals(Optional.empty())) {
				return result.get();
			}
		}
		return defaultValue;
	}

	/**
	 * Get the loop value
	 *
	 * @param attributeCode the attribute code
	 * @param defaultValue  the default value
	 * @param <T>           The Type to return
	 * @return T
	 */
	@JsonIgnore
	@JsonbTransient
	@Transient
	public <T> T getLoopValue(final String attributeCode, T defaultValue) {

		Optional<T> result = getLoopValue(attributeCode);
		if (result.isPresent()) {
			if (!result.equals(Optional.empty())) {
				return result.get();
			}
		}
		return defaultValue;
	}

	/**
	 * @param attributeCode the attribute code
	 * @return Boolean
	 */
	@JsonIgnore
	@JsonbTransient
	@Transient
	public Boolean is(final String attributeCode) {

		Optional<EntityAttribute> ea = this.findEntityAttribute(attributeCode);
		Boolean result = false;

		if (ea.isPresent()) {
			result = ea.get().getValueBoolean();
			if (result == null) {
				return false;
			}
		}
		return result;

	}

	/**
	 * Set the value
	 *
	 * @param attribute the attribute
	 * @param value     the value to set
	 * @param weight    the weight
	 * @param <T>       The Type to return
	 * @return Optional
	 * @throws BadDataException if value cannot be set
	 */
	@JsonIgnore
	@JsonbTransient
	@Transient
	public <T> Optional<T> setValue(final Attribute attribute, T value, Double weight) throws BadDataException {

		Optional<EntityAttribute> oldValue = this.findEntityAttribute(attribute.getCode());

		Optional<T> result = Optional.empty();
		if (oldValue.isPresent()) {
			if (oldValue.get().getLoopValue() != null) {
				result = Optional.of(oldValue.get().getLoopValue());
			}
			EntityAttribute ea = oldValue.get();
			ea.setValue(value);
			ea.setWeight(weight);
		} else {
			this.addAttribute(attribute, weight, value);
		}
		return result;
	}

	/**
	 * Set the value
	 *
	 * @param attribute the attribute
	 * @param value     the value to set
	 * @param <T>       The Type to return
	 * @return Optional
	 * @throws BadDataException if value cannot be set
	 */
	@JsonIgnore
	@JsonbTransient
	@Transient
	public <T> Optional<T> setValue(final Attribute attribute, T value) throws BadDataException {
		return setValue(attribute, value, 0.0);
	}

	/**
	 * Set the value
	 *
	 * @param attributeCode the attribute code
	 * @param value         the value to set
	 * @param <T>           The Type to return
	 * @return Optional
	 * @throws BadDataException if value cannot be set
	 */
	@JsonIgnore
	@JsonbTransient
	@Transient
	public <T> Optional<T> setValue(final String attributeCode, T value) throws BadDataException {
		return setValue(attributeCode, value, 0.0);
	}

	/**
	 * Set the value
	 *
	 * @param attributeCode the attribute code
	 * @param value         the value to set
	 * @param weight        the weight
	 * @param <T>           The Type to return
	 * @return Optional
	 * @throws BadDataException if value cannot be set
	 */
	@JsonIgnore
	@JsonbTransient
	@Transient
	public <T> Optional<T> setValue(final String attributeCode, T value, Double weight) throws BadDataException {
		Optional<EntityAttribute> oldValue = this.findEntityAttribute(attributeCode);

		Optional<T> result = Optional.empty();
		if (oldValue.isPresent()) {
			if (oldValue.get().getLoopValue() != null) {
				result = Optional.of(oldValue.get().getLoopValue());
			}
			EntityAttribute ea = oldValue.get();
			ea.setValue(value);
			ea.setWeight(weight);
		}
		return result;
	}

	/**
	 * Force private
	 *
	 * @param attribute the attribute to force
	 * @param state     should force
	 */
	@Transient
	public void forcePrivate(final Attribute attribute, final Boolean state) {
		forcePrivate(attribute.getCode(), state);
	}

	/**
	 * Force inferred
	 *
	 * @param attribute the attribute to force
	 * @param state     should force
	 */
	@Transient
	public void forceInferred(final Attribute attribute, final Boolean state) {
		forceInferred(attribute.getCode(), state);
	}

	/**
	 * Force readonly
	 *
	 * @param attribute the attribute to force
	 * @param state     should force
	 */
	@Transient
	public void forceReadonly(final Attribute attribute, final Boolean state) {
		forceReadonly(attribute.getCode(), state);
	}

	/**
	 * Force private
	 *
	 * @param attributeCode the code of the attribute to force
	 * @param state         should force
	 */
	@Transient
	public void forcePrivate(String attributeCode, Boolean state) {
		Optional<EntityAttribute> optEa = this.findEntityAttribute(attributeCode);
		if (optEa.isPresent()) {
			EntityAttribute ea = optEa.get();
			ea.setPrivacyFlag(state);
		}
	}

	/**
	 * Force inferred
	 *
	 * @param attributeCode the code of the attribute to force
	 * @param state         should force
	 */
	@Transient
	public void forceInferred(final String attributeCode, final Boolean state) {
		Optional<EntityAttribute> optEa = this.findEntityAttribute(attributeCode);
		if (optEa.isPresent()) {
			EntityAttribute ea = optEa.get();
			ea.setInferred(state);
		}
	}

	/**
	 * Force readonly
	 *
	 * @param attributeCode the code of the attribute to force
	 * @param state         should force
	 */
	@Transient
	public void forceReadonly(final String attributeCode, final Boolean state) {
		Optional<EntityAttribute> optEa = this.findEntityAttribute(attributeCode);
		if (optEa.isPresent()) {
			EntityAttribute ea = optEa.get();
			ea.setReadonly(state);
		}
	}

	@JsonbTransient
	public boolean isPerson() {
		return getCode().startsWith(Prefix.PER);
	}

	@Override
	public CoreEntitySerializable toSerializableCoreEntity() {
		life.genny.qwandaq.serialization.baseentity.BaseEntity baseEntitySerializable = new life.genny.qwandaq.serialization.baseentity.BaseEntity();
		baseEntitySerializable.setCode(getCode());
		baseEntitySerializable.setCreated(getCreated());
		// baseEntitySerializable.setDtype();
		baseEntitySerializable.setId(getId());
		baseEntitySerializable.setName(getName());
		baseEntitySerializable.setRealm(getRealm());
		baseEntitySerializable.setStatus(getStatus().ordinal());
		baseEntitySerializable.setUpdated(getUpdated());
		return baseEntitySerializable;
	}

	@Override
	public int hashCode() {
		return (this.getRealm()+this.getCode()).hashCode();
	}

	@Override
	public boolean equals(Object otherObject) {
		return this.getRealm().equals(((BaseEntity) otherObject).getRealm())
				&& this.getCode().equals(((BaseEntity) otherObject).getCode());
	}

	public HBaseEntity toHBaseEntity() {
		HBaseEntity hBaseEntity = new HBaseEntity();
		hBaseEntity.setCode(getCode());
		hBaseEntity.setCreated(getCreated());
		// hBaseEntity.setDtype();
		hBaseEntity.setId(getId());
		hBaseEntity.setName(getName());
		hBaseEntity.setRealm(getRealm());
		hBaseEntity.setStatus(getStatus());
		hBaseEntity.setUpdated(getUpdated());
		return hBaseEntity;
	}

	public BaseEntity clone(boolean includeAttributes) {
		BaseEntity clone = new BaseEntity();
		clone.setCode(getCode());
		clone.setCreated(getCreated());
		clone.setName(getName());
		clone.setRealm(getRealm());
		clone.setStatus(getStatus());
		clone.setUpdated(getUpdated());
		if(includeAttributes) {
			Map<String, EntityAttribute> baseEntityAttributesMap = getBaseEntityAttributesMap();
			Map<String, EntityAttribute> clonedBaseEntityAttributesMap = new HashMap<>(baseEntityAttributesMap.size());
			clone.setBaseEntityAttributes(clonedBaseEntityAttributesMap);
			for(Map.Entry<String, EntityAttribute> entry : baseEntityAttributesMap.entrySet()) {
				clonedBaseEntityAttributesMap.put(entry.getKey(), entry.getValue().clone());
			}
		}
		return clone;
	}
}
