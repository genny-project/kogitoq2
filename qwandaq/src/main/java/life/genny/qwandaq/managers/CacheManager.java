package life.genny.qwandaq.managers;

import life.genny.qwandaq.CoreEntity;
import life.genny.qwandaq.CoreEntityPersistable;
import life.genny.qwandaq.Question;
import life.genny.qwandaq.QuestionQuestion;
import life.genny.qwandaq.attribute.Attribute;
import life.genny.qwandaq.attribute.EntityAttribute;
import life.genny.qwandaq.data.GennyCache;
import life.genny.qwandaq.entity.BaseEntity;
import life.genny.qwandaq.exception.runtime.ItemNotFoundException;
import life.genny.qwandaq.models.UserToken;
import life.genny.qwandaq.serialization.CoreEntitySerializable;
import life.genny.qwandaq.serialization.attribute.AttributeKey;
import life.genny.qwandaq.serialization.baseentity.BaseEntityKey;
import life.genny.qwandaq.serialization.common.CoreEntityKey;
import life.genny.qwandaq.serialization.entityattribute.EntityAttributeKey;
import life.genny.qwandaq.utils.BaseEntityUtils;
import life.genny.qwandaq.utils.QuestionUtils;
import org.apache.commons.lang3.StringUtils;
import org.infinispan.client.hotrod.RemoteCache;
import org.infinispan.client.hotrod.Search;
import org.infinispan.query.dsl.Query;
import org.infinispan.query.dsl.QueryFactory;
import org.infinispan.query.dsl.QueryResult;
import org.jboss.logging.Logger;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.json.bind.Jsonb;
import javax.json.bind.JsonbBuilder;
import java.lang.reflect.Type;
import java.util.*;
import java.util.stream.Collectors;

/*
 * A utility class used for standard read and write 
 * operations to the cache.
 * 
 * @author Jasper Robison
 */
@ApplicationScoped
public class CacheManager {

    public static final String CACHE_NAME_BASEENTITY = "baseentity";
    public static final String CACHE_NAME_BASEENTITY_ATTRIBUTE = "baseentity_attribute";
    public static final String CACHE_NAME_ATTRIBUTE = "attribute";

	Jsonb jsonb = JsonbBuilder.create();

	private GennyCache cache;

	@Inject
	Logger log;

	@Inject
	UserToken userToken;

	@Inject
	QuestionUtils questionUtils;

	@Inject
	BaseEntityUtils baseEntityUtils;

	/**
	 * @param gennyCache the gennyCache to set
	 */
	public void init(GennyCache gennyCache) {
		cache = gennyCache;
	}

	/**
	* Clear a remote realm cache
	*
	* @param realm The realm of the cache to clear
	 */
	public void clear(String realm) {
		cache.getRemoteCache(realm).clear();
	}

	/**
	 * Read a stringified item from a realm cache.
	 *
	 * @param realm the realm to read from
	 * @param key the key to read
	 * @return Object
	 */
	public Object readCache(String realm, String key) {
		Object ret = cache.getRemoteCache(realm).get(key);
		return ret;
	}

	/**
	 * Write a stringified item to a realm cache.
	 *
	 * @param realm The realm cache to use.
	 * @param key   The key to save under.
	 * @param value The value to save.
	 * 
	 * @return returns the newly written value
	 */
	public String writeCache(String realm, String key, String value) {
		log.debugf("realm: %s, key: %s", realm, key);
		RemoteCache<String, String> remoteCache = cache.getRemoteCache(realm);
		remoteCache.put(key, value);
		return remoteCache.get(key);
	}

	/**
	* Remove an entry from a realm cache.
	*
	* @param realm The realm cache to remove from.
	* @param key The key of the entry to remove.
	 */
	public void removeEntry(String realm, String key) {
		cache.getRemoteCache(realm).remove(key);
	}

	/**
	 * Get an object from a realm cache using a {@link Class}.
	 *
	 * @param <T> the Type to cast as
	 * @param realm the realm to get from
	 * @param key the key to get
	 * @param c the Class to get as
	 * @return T
	 */
	public <T> T getObject(String realm, String key, Class<T> c) {
		String data = (String) readCache(realm, key);
		log.tracef("key: %s, value: %s", key, data);
		if (StringUtils.isEmpty(data)) {
			return null;
		}
		T object = jsonb.fromJson(data, c);
		return object;
	}

	/**
	 * Get an object from a realm cache using a {@link Type}.
	 *
	 * @param <T> the Type to cast as
	 * @param realm the realm to get from
	 * @param key the key to get
	 * @param t the Type to get as
	 * @return T
	 */
	public <T> T getObject(String realm, String key, Type t) {
		String data = (String) readCache(realm, key);
		if (data == null) {
			return null;
		}
		T object = jsonb.fromJson(data, t);
		return object;
	}

	/**
	 * Put an object into the cache.
	 *
	 * @param realm the realm to put object into
	 * @param key the key to put object under
	 * @param obj the obj to put
	 */
	public void putObject(String realm, String key, Object obj) {
		String json = jsonb.toJson(obj);
		cache.getRemoteCache(realm).put(key, json);
		log.tracef("Caching: [%s:%s]=%s", realm , key, obj);
	}

	/**
	* Get a CoreEntity object from the cache using a CoreEntityKey.
	* 
	* @param cacheName The cache to read from
	* @param key The key they item is saved against
	* @return The CoreEntity returned
	 */
	public CoreEntitySerializable getEntity(String cacheName, CoreEntityKey key) {
		return cache.getEntityFromCache(cacheName, key);
	}

	/**
	 * Get a CoreEntity object from the cache using a CoreEntityKey.
	 *
	 * @param cacheName The cache to read from
	 * @param key The key they item is saved against
	 * @return The CoreEntity returned
	 */
	public CoreEntityPersistable getPersistableEntity(String cacheName, CoreEntityKey key) {
		return cache.getPersistableEntityFromCache(cacheName, key);
	}

	/**
	* Save a {@link CoreEntity} to the cache using a CoreEntityKey.
	*
	* @param cacheName The cache to save to
	* @param key The key to save against
	* @param entity The CoreEntity to save
	* @return The CoreEntity being saved
	 */
	public boolean saveEntity(String cacheName, CoreEntityKey key, CoreEntityPersistable entity) {
		return cache.putEntityIntoCache(cacheName, key, entity);
	}

	/**
	 * Get a list of {@link CoreEntity}s to from cache by prefix.
	 * @param cacheName - Product Code / Cache to retrieve from
	 * @param prefix - Prefix of the Core Entity code to use
	 * @param callback - Callback to construct a {@link CoreEntityKey} for cache retrieval
	 * @return a list of core entities with matching prefixes
	 * 
	 * See Also: {@link CoreEntityKey}, {@link FICacheKeyCallback}
	 */
	public List<CoreEntity> getEntitiesByPrefix(String cacheName, String prefix, CoreEntityKey keyStruct) {
		List<CoreEntity> entities = cache.getRemoteCache(cacheName)
		.entrySet().stream().map((Map.Entry<String, String> entry) -> {
			String key = entry.getKey();
			CoreEntityKey currentKey = keyStruct.fromKey(key);

			return currentKey.getEntityCode().startsWith(prefix) ? jsonb.fromJson(entry.getValue(), CoreEntity.class) : null;
		})
		.filter(Objects::nonNull).collect(Collectors.toList());

		return entities;
	}

	/**
	 * Get a list of {@link BaseEntity}s to from cache by prefix.
	 * @param cacheName - Product Code / Cache to retrieve from
	 * @param prefix - Prefix of the Core Entity code to use
	 * @return a list of base entities with matching prefixes
	 * 
	 * See Also: {@link BaseEntityKey}, {@link CoreEntityKey#fromKey}, {@link CacheManager#getEntitiesByPrefix}
	 */
	public List<BaseEntity> getBaseEntitiesByPrefix(String cacheName, String prefix) {
		return getEntitiesByPrefix(cacheName, prefix, new BaseEntityKey())
		.stream().map((CoreEntity entity) -> (BaseEntity)entity).collect(Collectors.toList());
	}

    /**
     * @param productCode
     * @param code
     * @return
     */
    public Attribute get(String productCode, String code) {
        AttributeKey key = new AttributeKey(productCode, code);
		Attribute attribute = (Attribute) getPersistableEntity(CacheManager.CACHE_NAME_ATTRIBUTE, key);
		if (attribute == null) {
			throw new ItemNotFoundException(productCode, code);
		}
        return attribute;
    }

    /**
     * @param code
     * @return
     */
    public Attribute getAttribute(String code) {
		return getAttribute(userToken.getProductCode(), code);
    }

    /**
     * @param productCode
     * @param code
     * @return
     */
    public Attribute getAttribute(String productCode, String code) {
        AttributeKey key = new AttributeKey(productCode, code);
		Attribute attribute = (Attribute) getPersistableEntity(CacheManager.CACHE_NAME_ATTRIBUTE, key);
		if (attribute == null) {
			throw new ItemNotFoundException(productCode, code);
		}
		log.info("attribute = " + jsonb.toJson(attribute));
        return attribute;
    }

	/**
	 * Fetch all attributes for a product.
	 *
	 * @param productCode
	 * @return
	 */
	public Collection<Attribute> getAllAttributes(String productCode) {
		// get attribute cache
		RemoteCache<CoreEntityKey, CoreEntityPersistable> remoteCache = cache.getRemoteCacheForEntity(CACHE_NAME_ATTRIBUTE);
		return (Collection) remoteCache.values();
	}

	/**
	 * @param attribute
	 */
	public void saveAttribute(Attribute attribute) {
		life.genny.qwandaq.serialization.attribute.Attribute attr = (life.genny.qwandaq.serialization.attribute.Attribute) attribute.toSerializableCoreEntity();
		AttributeKey key = new AttributeKey(attribute.getRealm(), attribute.getCode());
		cache.putEntityIntoCache(CACHE_NAME_ATTRIBUTE, key, attr);
	}

	/**
	 * Get a list of {@link BaseEntity}s to from cache by prefix.
	 *
	 * @param productCode - Product Code to retrieve from
	 * @param prefix - Prefix of the Core Entity code to use
	 * @return a list of base entities with matching prefixes
	 *
	 * See Also: {@link BaseEntityKey}, {@link CoreEntityKey#fromKey}, {@link CacheManager#getEntitiesByPrefix}
	 */
	public List<life.genny.qwandaq.serialization.baseentity.BaseEntity> getBaseEntitiesByPrefixUsingIckle(String productCode, String prefix) {
		QueryFactory queryFactory = Search.getQueryFactory(cache.getRemoteCacheForEntity(CACHE_NAME_BASEENTITY));
		Query<life.genny.qwandaq.serialization.baseentity.BaseEntity> query = queryFactory
				.create("from life.genny.qwandaq.persistence.baseentity.BaseEntity where realm = '" + productCode
						+ "' and code like '" + prefix + "%'");
		QueryResult<life.genny.qwandaq.serialization.baseentity.BaseEntity> queryResult = query.execute();
		return queryResult.list();
	}

	/**
	 * @param ickleQuery
	 * @return
	 */
	public List<life.genny.qwandaq.serialization.baseentity.BaseEntity> getBaseEntitiesUsingIckle(String ickleQuery) {
		QueryFactory queryFactory = Search.getQueryFactory(cache.getRemoteCacheForEntity(CACHE_NAME_BASEENTITY));
		Query<life.genny.qwandaq.serialization.baseentity.BaseEntity> query = queryFactory.create(ickleQuery);
		QueryResult<life.genny.qwandaq.serialization.baseentity.BaseEntity> queryResult = query.execute();
		return queryResult.list();
	}

	/**
	 * Get a list of {@link EntityAttribute}s to from cache for a BaseEntity.
	 *
	 * @param productCode - Product Code / Cache to retrieve from
	 * @param baseEntityCode - Prefix of the Core Entity code to use
	 * @return a list of base entities with matching prefixes
	 *
	 * See Also: {@link BaseEntityKey}, {@link CoreEntityKey#fromKey}, {@link CacheManager#getEntitiesByPrefix}
	 */
	public List<EntityAttribute> getAllBaseEntityAttributesForBaseEntity(String productCode, String baseEntityCode) {
		RemoteCache<CoreEntityKey, CoreEntityPersistable> remoteCache = cache.getRemoteCacheForEntity(CACHE_NAME_BASEENTITY_ATTRIBUTE);
		QueryFactory queryFactory = Search.getQueryFactory(remoteCache);
		Query<EntityAttribute> query = queryFactory
				.create("from life.genny.qwandaq.persistence.entityattribute.EntityAttribute where realm = '" + productCode
						+ "' and baseEntityCode = '" + baseEntityCode + "'");
		QueryResult<EntityAttribute> queryResult = query.execute();
		return queryResult.list();
	}

	/**
	 * @param productCode
	 * @param questionCode
	 * @param fetchChildQuestions
	 * @return
	 */
	public Question getQuestion(String productCode, String questionCode) {
		// fetch baseentity representation
		BaseEntity baseEntity = baseEntityUtils.getBaseEntity(productCode, questionCode);
		log.info("Question BaseEntity Code = " + baseEntity.getCode());
		// fetch attributes and convert to question
		Question question = questionUtils.getQuestionFromBaseEntity(baseEntity, baseEntity.getBaseEntityAttributes());
		log.info("Question Code = " + question.getCode());
		// ensure attribute field is non null
		Attribute attribute = getAttribute(productCode, question.getAttributeCode());
		question.setAttribute(attribute);
		log.info("question = " + jsonb.toJson(question));
		return question;
	}

	/**
	 * @param productCode
	 * @param parentCode
	 * @return
	 */
	public List<QuestionQuestion> getQuestionQuestions(String productCode, String parentCode) {
		// get bea remote cache for querying
		RemoteCache<CoreEntityKey, CoreEntityPersistable> remoteCache = cache.getRemoteCacheForEntity(CACHE_NAME_BASEENTITY_ATTRIBUTE);
		QueryFactory queryFactory = Search.getQueryFactory(remoteCache);
		log.info("QuestionQuestion -> productCode = " + productCode + ", questionCode = " + parentCode);
		// init query
		Query<EntityAttribute> query = queryFactory
				.create("from life.genny.qwandaq.persistence.entityattribute.EntityAttribute where baseEntityCode like '"+parentCode+"|%'"
					 + "and realm = '"+productCode+"'");
		// execute query
		QueryResult<EntityAttribute> queryResult = query.execute();
		Question parent = getQuestion(productCode, parentCode);
		// begin building QQ objects
		List<QuestionQuestion> questionQuestions = new LinkedList<>();
		for (EntityAttribute entityAttribute : queryResult.list()) {
			String baseEntityCode = entityAttribute.getBaseEntityCode();
			log.debug("Fetching QuesQues -> " + baseEntityCode);
			String[] codes = StringUtils.split(baseEntityCode, '|');
			String childCode = codes[1];
			log.debug("Fetching question for child code -> " + childCode);
			Question child = getQuestion(productCode, childCode);
			QuestionQuestion questionQuestion = new QuestionQuestion(parent, child);
			questionQuestions.add(questionQuestion);
		}
		return questionQuestions;
	}

	/**
	 * @param question
	 */
	public void saveQuestion(Question question) {
		life.genny.qwandaq.serialization.baseentity.BaseEntity baseEntity = questionUtils.getSerializableBaseEntityFromQuestion(question);
		BaseEntityKey bek = new BaseEntityKey(baseEntity.getRealm(), baseEntity.getCode());
		cache.putEntityIntoCache(CACHE_NAME_BASEENTITY, bek, baseEntity);
		questionUtils.getSerializableBaseEntityAttributesFromQuestion(question).parallelStream().forEach(baseEntityAttribute -> {
			EntityAttributeKey beak = new EntityAttributeKey(baseEntityAttribute.getRealm(), baseEntityAttribute.getBaseEntityCode(), baseEntityAttribute.getAttributeCode());
			cache.putEntityIntoCache(CACHE_NAME_BASEENTITY_ATTRIBUTE, beak, baseEntityAttribute);
		});
		question.getChildQuestions().parallelStream().forEach(questionQuestion -> saveQuestionQuestion(questionQuestion));
	}

	/**
	 * @param questionQuestion
	 */
	public void saveQuestionQuestion(QuestionQuestion questionQuestion) {
		life.genny.qwandaq.serialization.baseentity.BaseEntity baseEntity = questionUtils.getSerializableBaseEntityFromQuestionQuestion(questionQuestion);
		BaseEntityKey bek = new BaseEntityKey(baseEntity.getRealm(), baseEntity.getCode());
		cache.putEntityIntoCache(CACHE_NAME_BASEENTITY, bek, baseEntity);
		questionUtils.getSerializableBaseEntityAttributesFromQuestionQuestion(questionQuestion).parallelStream().forEach(baseEntityAttribute -> {
			EntityAttributeKey beak = new EntityAttributeKey(baseEntityAttribute.getRealm(), baseEntityAttribute.getBaseEntityCode(), baseEntityAttribute.getAttributeCode());
			cache.putEntityIntoCache(CACHE_NAME_BASEENTITY_ATTRIBUTE, beak, baseEntityAttribute);
		});
	}
}

