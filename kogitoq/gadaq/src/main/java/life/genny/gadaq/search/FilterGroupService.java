package life.genny.gadaq.search;

import life.genny.kogito.common.service.FilterService;
import life.genny.kogito.common.service.FilterService.Options;
import life.genny.qwandaq.attribute.Attribute;
import life.genny.qwandaq.attribute.EntityAttribute;
import life.genny.qwandaq.constants.FilterConst;
import life.genny.qwandaq.datatype.DataType;
import life.genny.qwandaq.entity.BaseEntity;
import life.genny.qwandaq.entity.search.trait.Operator;
import life.genny.qwandaq.message.QDataAnswerMessage;
import life.genny.qwandaq.message.QEventMessage;
import life.genny.qwandaq.models.SavedSearch;
import life.genny.qwandaq.models.UserToken;
import life.genny.qwandaq.utils.CacheUtils;
import life.genny.qwandaq.utils.DatabaseUtils;
import life.genny.qwandaq.utils.QwandaUtils;
import org.jboss.logging.Logger;

import javax.enterprise.context.ApplicationScoped;
import javax.json.JsonObject;
import javax.json.bind.Jsonb;
import javax.json.bind.JsonbBuilder;
import java.lang.invoke.MethodHandles;
import java.util.stream.Collectors;
import java.util.Set;
import java.util.Optional;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.util.UUID;
import java.util.Comparator;
import javax.inject.Inject;
import life.genny.qwandaq.utils.BaseEntityUtils;
import life.genny.qwandaq.Question;


@ApplicationScoped
public class FilterGroupService {
    private static final Logger log = Logger.getLogger(MethodHandles.lookup().lookupClass());

    static Jsonb jsonb = JsonbBuilder.create();

    @Inject
    BaseEntityUtils beUtils;

    @Inject
    QwandaUtils qwandaUtils;

    @Inject
    UserToken user;

    @Inject
    FilterService filterService;

    @Inject
    DatabaseUtils databaseUtils;

    @Inject
    UserToken userToken;

    public static final String QUE_TABLE_PREF = "QUE_TABLE_";
    public static final String SBE_TABLE_PREF = "SBE_TABLE_";

    /* Bucket */
    public static final String PREF_CPY = "CPY_";
    public static final String QUE_TAB_BUCKET_VIEW = "QUE_TAB_BUCKET_VIEW";
    public static final String PRI_PREFIX = "PRI_PREFIX";
    public static final String LNK_SAVED_SEARCHES = "LNK_SAVED_SEARCHES";
    public  static final DataType DataTypeStr = DataType.getInstance("life.genny.qwanda.entity.BaseEntity");
    public static final String DELETE = "Delete";
    public static final String QUE_ADD_SEARCH = "QUE_ADD_SEARCH";

    /**
     * Check code whether is filter select question or not
     * @param value Filter Value
     * @return Being filter option
     */
    public  boolean isSelectBox(String value) {
        boolean result = false;
        if(value.contains(FilterConst.SELECT)) return true;
        return result;
    }

    /**
     * Check code whether is filter value or not
     * @param code Message Code
     * @return Being filter value
     */
    public  boolean isValueSelected(String code) {
        boolean result = false;
        if(code != null && code.startsWith(FilterConst.QUE_FILTER_VALUE_PREF)) return true;
        return result;
    }

    /**
     * Check code whether is filter submit or not
     * @param code Message Code
     * @return Being filter submit
     */
    public  boolean isApply(String code) {
        boolean result = false;
        if(code != null && code.equalsIgnoreCase(FilterConst.QUE_SAVED_SEARCH_APPLY)) return true;
        return result;
    }

    /**
     * Check code whether is quesion showing filter box or not
     * @param code Message Code
     * @return Being question showing filter box
     */
    public  boolean isValidTable(String code) {
        boolean result = false;
        if(code!=null &&  code.startsWith(QUE_TABLE_PREF)) return true;
        return result;
    }

    /**
     * Being filter optio whether selected or not
     * @param code Event Code
     * @param attCode Attribute Code
     * @return filter option selected
     */
    public boolean isColumnSelected(String code,String attCode) {
        boolean result = false;
        if(code != null && code.startsWith(FilterConst.QUE_FILTER_COLUMN)) return true;
        if(attCode !=null && attCode.startsWith(FilterConst.LNK_FILTER_COLUMN)) return true;

        return result;
    }

    /**
     * Being filter optio whether selected or not
     * @param code Event Code
     * @param attCode Attribute Code
     * @return filter option selected
     */
    public boolean isOptionSelected(String code, String attCode) {
        boolean result = false;
        if(code != null && code.startsWith(FilterConst.QUE_FILTER_OPTION)) return true;
        if(attCode!=null && attCode.startsWith(FilterConst.LNK_FILTER_OPTION)) return true;

        return result;
    }

    /**
     * Being filter optio whether selected or not
     * @param code Event Code
     * @param value Message value
     * @return filter option selected
     */
    public boolean isSearchSelected(String code,String value) {
        boolean result = false;
        if(code!=null && code.startsWith(FilterConst.QUE_SAVED_SEARCH_SELECT)
                && value!=null && value.startsWith(FilterConst.SBE_SAVED_SEARCH)) {
            return true;
        }
        return result;
    }

    /**
     * Return question code by filter code
     * @param value Filter Code
     * @return Return question code by filter code
     */
    public String getQuestionCodeByValue(String value){
        if(value.contains(FilterConst.DATETIME)) return FilterConst.QUE_FILTER_VALUE_DATETIME;
        if(value.contains(FilterConst.COUNTRY)) return FilterConst.QUE_FILTER_VALUE_COUNTRY;
        if(!isSelectBox(value)) return FilterConst.QUE_FILTER_VALUE_TEXT;

        String valSuffix = getLinkValCode(value);
        String questionCode = FilterConst.QUE_FILTER_VALUE_PREF + valSuffix;

        return questionCode;
    }

    /**
     * Return attribute code by question
     * @param questionCode Question Code
     * @return Return question code by filter code
     */
    public String getAttributeCodeByQuestion(String questionCode){
        Question question = databaseUtils.findQuestionByCode(user.getProductCode(), questionCode);
        return question.getAttributeCode();
    }

    /**
     * Return the last suffix code
     * @param value Filter Value
     * @return Return the last suffix code
     */
    public String getLinkValCode(String value) {
        return filterService.getLinkValueCode(value);
    }

    /**
     * Return the last suffix code
     * @param value Filter Value
     * @return Return the last suffix code
     */
    public String getColumnName(String value) {
        String lastSuffix = "";
        int lastIndex = value.lastIndexOf(FilterConst.PRI_PREFIX) + FilterConst.PRI_PREFIX.length();
        if(lastIndex > -1) {
            lastSuffix = value.substring(lastIndex, value.length());
            lastSuffix = lastSuffix.replaceFirst("\"]","");
        }
        return lastSuffix;
    }

    /**
     * Being whether event is pagination event or not
     * @param code Question code
     * @return Being whether event is pagination event or not
     */
    public boolean isPaginationEvent(String code) {
        if(code.equalsIgnoreCase(FilterConst.PAGINATION_NEXT)
                || code.equalsIgnoreCase(FilterConst.PAGINATION_PREV)) {
            return true;
        }

        return false;
    }

    /**
     * Being whether event is pagination event or not
     * @param code Event code
     * @return Being whether event is pagination event or not
     */
    public boolean isBucketPagination(String code) {
        if(code.equalsIgnoreCase(FilterConst.QUE_TABLE_LAZY_LOAD)) return true;
        return false;
    }

    /**
     * Being whether bucket filter select options or not
     * @param eventCode Event code
     * @param targetCode Target code
     * @param value Event value
     * @return Being whether bucket filter select options or not
     */
    public boolean isQuickSearchSelectOptions(String eventCode, String targetCode, String value) {
        boolean result = false;
        if(eventCode.startsWith(FilterConst.QUE_SELECT_INTERN) && targetCode.startsWith(FilterConst.BKT_APPLICATIONS)
                && !value.isEmpty())
            return true;

        return result;
    }


    /**
     * Being whether bucket filter select options or not
     * @param code Event code
     * @param attrCode Attribute code
     * @param targetCode Target code
     * @param value Event value
     * @return Being whether bucket filter select options or not
     */
    public boolean isQuickSearchSelectChanged(String code, String attrCode, String targetCode, String value) {
        boolean result = false;
        String newVal =  getStripSelectValue(value);

        if(code!=null && code.startsWith(FilterConst.QUE_SELECT_INTERN)
                && attrCode.startsWith(FilterConst.LNK_PERSON)
                && targetCode.startsWith(FilterConst.BKT_APPLICATIONS)
                && newVal.startsWith(PREF_CPY))
            return true;

        return result;
    }

    /**
     * Get stripped select value
     * @param value Select value chosen
     * @return Select value
     */
    public String getStripSelectValue(String value) {
        String finalVal = value.replace("\"","")
                .replace("[","").replace("]", "");

        return finalVal;
    }

    /**
     * Return base entity name
     * @param baseEntityCode Base entity code
     * @return Base entity name
     */
    public String getBaseNameByCode(String baseEntityCode) {
        return beUtils.getBaseEntityByCode(baseEntityCode).getName();
    }

    /**
     * Being whether sorting  or not
     * @param attrCode Attribute code
     * @param targetCode Target code
     * @return Being whether sorting  or not
     */
    public boolean isSorting(String attrCode, String targetCode) {
        if(attrCode.matches("SRT_.*") && targetCode.matches("SBE_.*")) {
            return true;
        }

        return false;
    }

    /**
     * Being whether search text  or not
     * @param attrCode Attribute code
     * @return Being whether search text  or not
     */
    public boolean isSearchText(String attrCode) {
        if(attrCode.equalsIgnoreCase(FilterConst.SEARCH_TEXT)) {
            return true;
        }

        return false;
    }

    /**
     * Check code whether is quesion showing filter box or not
     * @param code Message Code
     * @return Being question showing filter box
     */
    public  boolean isValidBucket(String code) {
        boolean result = false;
        if(code != null && code.startsWith(QUE_TAB_BUCKET_VIEW)) return true;
        return result;
    }

    /**
     *  Being whether filter button or not
     * @param msg Event message
     * @return Being whether it was sent or not
     */
    public boolean isFilter(QEventMessage msg) {
        String code = msg.getData().getCode();
        boolean result = isValidTable(code) || isValidBucket(code) || isFilterBtn(code) || isAddFilterGroup(msg);
        return result;
    }

    /**
     *  Being whether filter button or not
     * @param code Event code
     * @return Being whether it was sent or not
     */
    public boolean isFilterBtn(String code) {
        boolean result = isApply(code) || isBtnSearchAdd(code) || isBtnSearchDelete(code) || isBtnSearchSave(code);

        return result;
    }

    public boolean isFilterBtn(QEventMessage msg) {
        String code = msg.getData().getCode();
        boolean result = isApply(code) || isBtnSearchAdd(code) || isBtnSearchDelete(code) || isBtnSearchSave(code);

        return result;
    }


    /**
     *  Being whether filter event or not
     * @param msg Event Message
     * @return Being whether it was sent or not
     */
    public boolean isValidEvent(QDataAnswerMessage msg) {
        String code = getQuestionCode(msg);
        String attCode = getAttributeCode(msg);
        String value = getValue(msg);

        boolean result =  isColumnSelected(code,attCode) || isOptionSelected(code,attCode)
                || isValueSelected(code) || isFilterBtn(code) || isQuickSearch(code) || isSearchSelected(code,value);

        return result;
    }

    /**
     *  Send  quick search and filter group
     * @param code Event code
     * @return Being whether it was sent or not
     */

    public boolean isFilterAndQuickSearch(String code) {
        boolean result = isValidTable(code) || isValidBucket(code) || isApply(code);
        return result;
    }

    /**
     * Return add fitler group or not
     * @param msg Event message
     * @return Whether being add fitler group or not
     */
    public boolean isAddFilterGroup(QEventMessage msg) {
        if(msg.getData().getParentCode() !=null
                && msg.getData().getParentCode().equalsIgnoreCase(FilterConst.QUE_ADD_FILTER_SBE_GRP)) {
            return true;
        }
        return false;
    }

    /**
     * Being whether add search button or not
     * @param code Code
     * @return Being whether add search button or not
     */
    public  boolean isBtnSearchAdd(String code) {
        boolean result = false;
        if(code != null && code.startsWith(QUE_ADD_SEARCH))
            return true;
        return result;
    }

    /**
     * Search quick text
     * @param msg Parsed message
     * @param value Message value
     * @param targetCode Target code
     */
    public void searchQuickText(JsonObject msg, String value, String targetCode) {
        String attrCode = Attribute.PRI_NAME;
        String attrName = Operator.LIKE.toString();
        String text =  "%" + value.replaceFirst("!","") + "%";
//        List<String> targetCodes =  EventMessageUtils.getTargetCodes(msg);
        List<String> targetCodes =  new ArrayList<>();

        /* Go to bucket */
        if (targetCodes.size() > 1) {
            filterService.handleBucketSearch(attrCode, attrName, text, targetCodes);
            /* Go to search text */
        }else {
            filterService.handleSortAndSearch(attrCode, attrName, text, targetCode, Options.SEARCH);
            filterService.sendQuickSearch(FilterConst.QUE_TABLE_FILTER_GRP,FilterConst.QUE_SELECT_INTERN,
                    FilterConst.LNK_PERSON, FilterConst.BKT_APPLICATIONS);
        }
    }

    /**
     * Handle event when filter columns is selected
     * @param value Message value
     * @return Attribute code according to value selected
     */
    public String selectFilerColumn(String value) {
        String sbeCode = getCachedSbeTable();
        String queCode = getQuestionCodeByValue(value);
        String attCode = getAttributeCodeByQuestion(queCode);

        String filterCode = "";
        Map<String, Map<String, String>> params = new HashMap<>();
        filterService.sendFilterOption(queCode, sbeCode);
        filterService.sendAddFilterGroup(FilterConst.QUE_ADD_FILTER_SBE_GRP,queCode,filterCode,params);

        boolean selectBox = isSelectBox(value);
        if(selectBox) {
            String linkVal = getLinkValCode(value);
            filterService.sendFilterValue(FilterConst.QUE_ADD_FILTER_SBE_GRP,queCode,FilterConst.LNK_CORE,linkVal,attCode);
        }

        return attCode;
    }

    /**
     * Handle event if selecting value in quick search
     * @param token Message token
     * @param attrCode Attribute code
     * @param attrName Attribute name
     * @param value Message value
     */
    public void selectQuickSearch(String token, String attrCode, String attrName,String value) {
        user.init(token);
        List<String> bucketCodes = filterService.getBucketCodesBySBE(FilterConst.SBE_TAB_BUCKET_VIEW);
        String baseCode = getStripSelectValue(value);
        String newVal = getBaseNameByCode(baseCode);

        filterService.handleBucketSearch(attrCode, attrName, newVal, bucketCodes);
    }

    /**
     * Being whether saved search or not
     * @param code Event Code
     * @return Being whether save button  or not
     */
    public boolean isBtnSearchSave(String code) {
        boolean result = false;
        if(code!=null && code.startsWith(FilterConst.QUE_SAVED_SEARCH_SAVE))
            return true;

        return result;
    }

    /**
     * Being whether saved search or not
     * @param code Event Code
     * @return Being whether delete button  or not
     */
    public boolean isBtnSearchDelete(String code) {
        boolean result = false;
        if(code!=null && code.startsWith(FilterConst.QUE_SAVED_SEARCH_DELETE))
            return true;

        return result;
    }

    /**
     * Save search
     * @param name Event name
     */
    public void saveSearch(String name) {
        Map<String,SavedSearch> params = getParamsFromCache();
        BaseEntity base = saveBaseEntity(name,params);
        String filterCode = base.getCode();

        filterService.sendListSavedSearches(FilterConst.QUE_SAVED_SEARCH_SELECT_GRP, FilterConst.QUE_SAVED_SEARCH_SELECT,
                FilterConst.PRI_NAME,FilterConst.VALUE);
    }

    /**
     * Save base entity
     * @param name Search name
     * @param params Parameters
     */
    public BaseEntity saveBaseEntity(String name,Map<String, SavedSearch> params) {
        BaseEntity baseEntity = null;

        try {
            String prefix = FilterConst.SBE_SAVED_SEARCH + "_";
            BaseEntity defBE = new BaseEntity(prefix);
            defBE.setRealm(user.getProductCode());
            String baseCode = prefix + UUID.randomUUID().toString();

            // create the main base entity
            String attCode = FilterConst.LNK_SAVED_SEARCHES;
            Attribute attr = new Attribute(PRI_PREFIX, attCode, DataTypeStr);
            defBE.addAttribute(attr, 1.0, FilterConst.SBE_PREF);
            baseEntity = beUtils.create(defBE, name, baseCode);

            Attribute attrFound = qwandaUtils.getAttribute(user.getProductCode(),attCode);

            // array of parameters
            List<String> listUUID = getListUUID(prefix,params.entrySet().size());
            String strLnkArr = convertLnkArrayToString(listUUID);

            baseEntity.addAttribute(attrFound, 1.0, strLnkArr);
            beUtils.updateBaseEntity(baseEntity);

            // create child base entities
            Attribute childAttr = new Attribute(PRI_PREFIX, attCode, DataTypeStr);
            BaseEntity childDefBE = new BaseEntity(prefix);
            childDefBE.setRealm(user.getProductCode());
            childDefBE.addAttribute(childAttr, 1.0, prefix);

            //create other base entities based on the main base entity
            int index = 0;
            for(Map.Entry<String,SavedSearch> entry : params.entrySet()) {
                String childBaseCode = listUUID.get(index);

                BaseEntity childBase = beUtils.create(childDefBE, name, childBaseCode);
                String childVal = jsonb.toJson(entry.getValue());
                childBase.addAttribute(attrFound, 1.0,childVal);
                beUtils.updateBaseEntity(childBase);
                index++;
            }

        }catch (Exception ex) {
            log.error(ex);
        }

        return baseEntity;
    }

    /**
     * Return list of UUID
     * @param size Size
     * @return list of UUID
     */
    public List<String> getListUUID(String prefix,int size) {
        List<String> list = new ArrayList<>();
        for(int i=0; i< size;i++) {
            String uuid =   prefix + UUID.randomUUID().toString().toUpperCase();
            list.add(uuid);
        }
        return list;
    }

    /**
     * Get string of link array
     * @param params Parameters
     * @return string of link array
     */
    public String convertLnkArrayToString(List<String> params) {
        String result = "";
        for(int i=0; i< params.size();i++) {
            if(params.size() == 1) {
                return "[" + "\"" + params.get(i) + "\"" + "]";
            }

            if(i == 0) {
                result = "[" + "\"" + params.get(i) + "\"" + ",";
            }else if(i == params.size() - 1) {
                result = result + "\"" + params.get(i) + "\"" + "]";
            } else {
                result = result + "\"" + params.get(i) + "\"" + ",";
            }
        }
        return result;
    }


    /**
     * Delete base entity
     * @param code Base entity
     */
    public void deleteSearch(String code) {
        databaseUtils.deleteBaseEntityAndAttribute(userToken.getProductCode(), code);
    }

    /**
     * Delete base entity
     * @param code Base entity
     */
    public void deleteSearches(String code) {
        String codes = beUtils.getBaseEntityValueAsString(code,LNK_SAVED_SEARCHES);
        List<BaseEntity> bases = beUtils.convertCodesToBaseEntityArray(codes);
        /* delete primary search */
        deleteSearch(code);

        /* delete other searchs */
        for(BaseEntity base : bases) {
            deleteSearch(base.getCode());
        }
    }

    /**
     * Save searches
     * @param filterCode Filter code
     */
    public void handleDeleteSearch(String filterCode) {
        deleteSearches(filterCode);

        filterService.sendListSavedSearches(FilterConst.QUE_SAVED_SEARCH_SELECT_GRP,
                FilterConst.QUE_SAVED_SEARCH_SELECT, FilterConst.PRI_NAME,FilterConst.VALUE);
    }

    /**
     * Get String value of base entity by attribute code
     * @param base Base entity
     * @param attCode Attribute code
     * @return String value of base entity by attribute code
     */
    public String getValueStringByAttCode(BaseEntity base, String attCode) {
        String value = "";

        Set<EntityAttribute> attributeSet =  base.getBaseEntityAttributes();
        Optional<EntityAttribute> ea = attributeSet.stream().filter(e -> e.getAttributeCode().equalsIgnoreCase(attCode))
                .findFirst();
        if(ea.isPresent()) {
            return ea.get().getValueString();
        }

        return value;
    }

    /**
     * Get the table of filter parameters
     * @param filterCode Filter code
     * @return Get the table of filter parameters
     */
    public Map<String,Map<String, String>> getFilterParamByBaseCode(String filterCode) {
        Map<String,Map<String, String>>  result =  new HashMap<>();

        String value = "";
        try {
            // get the filter by base entity code
            BaseEntity base = beUtils.getBaseEntityByCode(filterCode);
            value = getValueStringByAttCode(base,LNK_SAVED_SEARCHES);

            result = jsonb.fromJson(value, Map.class);
        }catch(Exception ex) {}

        return result;
    }

    /**
     * Get the table of filter parameters
     * @param filterCode Filter code
     * @return Get the table of filter parameters
     */
    public Map<String,SavedSearch> getFilterParamsByBaseCode(String filterCode) {
        Map<String,SavedSearch>  result =  new HashMap<>();

        try {
            // get the filter by base entity code
            String codes = beUtils.getBaseEntityValueAsString(filterCode,LNK_SAVED_SEARCHES);
            List<BaseEntity> bases = beUtils.convertCodesToBaseEntityArray(codes);

            for(BaseEntity base : bases) {
                String value = getValueStringByAttCode(base,LNK_SAVED_SEARCHES);
                SavedSearch ss = jsonb.fromJson(value, SavedSearch.class);

                result.put(base.getCode(),ss);
            }
        }catch(Exception ex) {
            log.error(ex);
        }

        CacheUtils.putObject(user.getProductCode(),getCachedAnswerKey(),result);
        return result;
    }

    /**
     * Get the latest filter code
     * @param sbeCode Search base entity code
     * @return The latest filter code
     */
    public String getLatestFilterCode(String sbeCode) {
        String filterCode = "";
        List<BaseEntity> bases = filterService.getListSavedSearches(sbeCode,FilterConst.PRI_NAME,
                FilterConst.VALUE);
        List<BaseEntity> basesSorted =  bases.stream()
                .sorted(Comparator.comparing(BaseEntity::getId).reversed())
                .collect(Collectors.toList());

        if(basesSorted.size() > 0) {
            return basesSorted.get(0).getCode();
        }
        return filterCode;
    }

    /**
     * Send filter and quick search data
     * @param code Code
     * @param queGroup Question group
     * @param sbeCode Search base entity code
     * @params filters Filter parameters
     */
    public void sendFilterAndQuickSearch(String code,String queGroup, String sbeCode, String filterCode,
                                         Map<String,Map<String, String>> filters, boolean isSubmitted) {

        filterService.sendQuickSearch(queGroup,FilterConst.QUE_SELECT_INTERN, FilterConst.LNK_PERSON,
                FilterConst.BKT_APPLICATIONS);

        /* get the latest filter code if filterCode is empty */
        if(filterCode.isEmpty()) {
            filterCode = getLatestFilterCode(sbeCode);
        }

        /* get the latest of filter */
        filterService.sendFilterColumns(sbeCode);

        /* send saved searches */
        String newSbe = filterService.getSearchBaseEntityCodeByJTI(sbeCode);
        String queCode = FilterConst.QUE_SAVED_SEARCH_LIST;

        /* send saved search list */
        filterService.sendListSavedSearches(queGroup,queCode,FilterConst.PRI_NAME,FilterConst.VALUE);

    }


    /**
     * Handle saved search selected
     * @param queCode Event code
     */
    public void handleSearchSelected(String queCode,String filterCode) {
        Map<String,SavedSearch>  params = getFilterParamsByBaseCode(filterCode);

        BaseEntity base = new BaseEntity(queCode);
        filterService.sendPartialPCM(FilterConst.PCM_SBE_DETAIL_VIEW, FilterConst.PRI_LOC1, base.getCode());
        filterService.sendFilterDetailsByBase(base,FilterConst.QUE_SBE_DETAIL_QUESTION_GRP,base.getCode(),params);
    }


    /**
     * Handle filter event by apply button
     */
    public void handleFilter() {
        Map<String,SavedSearch> params = getParamsFromCache();
        String sbeCode = getSbeTableFromCache();
        filterService.handleFilter(sbeCode, params);
    }

    /**
     * Handle sorting
     * @param attrCode Attribute code
     * @param attrName Attribute name
     * @param value Event Value
     * @param targetCode Target code
     */
    public void handleSorting(String attrCode,String attrName,String value,String targetCode) {
        filterService.handleSortAndSearch(attrCode,attrName,value,targetCode, Options.SEARCH);
        filterService.sendQuickSearch(FilterConst.QUE_TABLE_FILTER_GRP,FilterConst.QUE_SELECT_INTERN,
                FilterConst.LNK_PERSON, FilterConst.BKT_APPLICATIONS);
    }

    /**
     * Send base entity and question
     * @param queCode Question code
     * @param attCode Filter Option
     * @param value Filter Value
     */
    public void sendFilterDetails(String queCode,String attCode,String value) {
        Map<String,SavedSearch> params = getParamsFromCache();

        BaseEntity base = new BaseEntity(queCode);
        filterService.sendPartialPCM(FilterConst.PCM_SBE_DETAIL_VIEW, FilterConst.PRI_LOC1, base.getCode());
        filterService.sendFilterDetailsByBase(base,FilterConst.QUE_SBE_DETAIL_QUESTION_GRP,base.getCode(),params);
    }


    /**
     * Put answers in cache
     * @param attCode Attribute code
     * @param value Values
     */
    public void putAnswerstoCache(String attCode,String value) {
        Map<String, SavedSearch> params = CacheUtils.getObject(user.getProductCode(),getCachedAnswerKey() ,Map.class);
        SavedSearch savedSearch = new SavedSearch(attCode,value);

        params.put(UUID.randomUUID().toString() ,savedSearch);
        CacheUtils.putObject(user.getProductCode(),getCachedAnswerKey(),params);
    }

    /**
     * Cached answer key
     * @return Cached answer key
     */
    public String getCachedAnswerKey() {
        String key = FilterConst.LAST_ANSWERS_MAP + ":" + user.getUserCode();
        return key;
    }

    /**
     * Cached sbe table code
     * @return Sbe table code
     */
    public String getCachedSbeTable() {
        String key = FilterConst.LAST_SBE_TABLE + ":" + user.getUserCode();
        return key;
    }

    /**
     * Get question code
     * @param msg Message object
     * @return Question code
     */
    public String getQuestionCode(QEventMessage msg) {
        if(msg.getData() !=null) {
            return msg.getData().getCode();
        }
        return "";
    }

    /**
     * Get question code
     * @param msg Message object
     * @return Question code
     */
    public String getQuestionCode(QDataAnswerMessage msg) {
        if(msg != null &&  msg.getItems().length > 0) {
            return msg.getItems()[0].getCode();
        }
        return "";
    }

    /**
     * Get attribute code
     * @param msg Message object
     * @return Attribute code
     */
    public String getAttributeCode(QDataAnswerMessage msg) {
        if(msg.getItems().length > 0) {
            return msg.getItems()[0].getAttributeCode();
        }
        return "";
    }

    /**
     * Get target code
     * @param msg Message object
     * @return target code
     */
    public String getTargetCode(QDataAnswerMessage msg) {
        if(msg.getItems().length > 0) {
            return msg.getItems()[0].getTargetCode();
        }
        return "";
    }

    /**
     * Get value
     * @param msg Message object
     * @return value
     */
    public String getValue(QDataAnswerMessage msg) {
        if(msg.getItems().length > 0) {
            return msg.getItems()[0].getValue();
        }
        return "";
    }

    /**
     * Get search code
     * @param msg Message object
     * @return search code
     */
    public String getSearchCode(QDataAnswerMessage msg) {
        return "";
    }

    /**
     * Get parent code
     * @param msg Message object
     * @return Parent code
     */
    public String getParentCode(QDataAnswerMessage msg) {
        return "";
    }


    /**
     *  Send  quick search and filter group
     * @param code Event code
     * @return Being whether it was sent or not
     */

    public boolean isQuickSearch(String code) {
        if(code !=null && code.equalsIgnoreCase(FilterConst.QUE_SEARCH)) {
            return true;
        }
        return false;
    }


    /**
     * Handle filter data event
     * @param msg Event message
     */
    public void handleBtnEvents(QEventMessage msg) {
        try {
            String token = msg.getToken();
            String code = msg.getData().getCode();
            String attrCode = msg.getAttributeCode();
            String attrName = "";
            String value = msg.getData().getValue();
            String targetCode = msg.getData().getTargetCode();

            // init user token
            if(!token.isEmpty()) { user.init(token);}

            /* Button save search */
            if(isBtnSearchSave(code)) {
                saveSearch(value);return;
            }
            /* apply filter */
            if(isApply(code)) {
                handleFilter();return;
            }
            /* Button delete search */
            if(isBtnSearchDelete(code)) {
                 handleDeleteSearch(value);
            }

            // Quick search selected
            if(isQuickSearchSelectChanged(code, attrCode, targetCode, value)) {
                selectQuickSearch(token, attrCode, attrName, value);
            }

        } catch (Exception ex){
            log.error(ex);
        }
    }

    /**
     * Handle filter data event
     * @param msg Answer Message
     */
    public void handleDataEvents(QDataAnswerMessage msg) {
        try {
            String token = msg.getToken();
            String code = getQuestionCode(msg);
            String attrCode = getAttributeCode(msg);
            String attrName = "";
            String value = getValue(msg);
            String targetCode = msg.getTargetCode();

            // init user token
            if(!token.isEmpty()) { user.init(token);}

            /* Quick search */
            if(isQuickSearch(code)) {
                handleQuickSearch(msg);
                return;
            }
            /* Go to Column */
            if(isColumnSelected(code,attrCode)) {
                String queValCode = selectFilerColumn(value);
                filterService.sendPartialPCM(FilterConst.PCM_SBE_ADD_SEARCH, FilterConst.PRI_LOC3, queValCode);
                return;
            }
            /* add button*/
            if(isBtnSearchAdd(code)) {
                putAnswerstoCache(attrCode,value);
                sendFilterDetails(code,attrCode,value);
                return;
            }
            /* saved search saved */
            if(isSearchSelected(code,value)) {
                handleSearchSelected(code,value);
            }

            /* Quick search selected */
            if(isQuickSearchSelectChanged(code, attrCode, targetCode, value)) {
                selectQuickSearch(token, attrCode, attrName, value);
            }

        } catch (Exception ex){
            log.error(ex);
        }
    }

    /**
     * Handle filter data event
     * @param msg Answer Message
     */
    public void handleQuickSearch(QDataAnswerMessage msg) {
        try {
            String value = getValue(msg);
            String targetCode = getTargetCode(msg);

            filterService.handleQuickSearch(value,targetCode);

        } catch (Exception ex){
            log.error(ex);
        }
    }


    /**
     * Initialize Cache
     * @param queCode Question code
     */
    public void init(String queCode) {
        clearParamsInCache();
        String sbe = queCode.replaceFirst(FilterConst.QUE_PREF,FilterConst.SBE_PREF);
        filterService.sendFilterColumns(sbe);
        CacheUtils.putObject(user.getProductCode(),getCachedSbeTable(), sbe);

        filterService.sendListSavedSearches(FilterConst.QUE_SAVED_SEARCH_SELECT_GRP,
                FilterConst.QUE_SAVED_SEARCH_SELECT, FilterConst.PRI_NAME,FilterConst.VALUE);
    }

    /**
     * Return parameter from cache
     * @return Parameters from cache
     */
    public Map<String,SavedSearch> getParamsFromCache() {
        Map<String, SavedSearch> params = CacheUtils.getObject(user.getProductCode(),getCachedAnswerKey() ,Map.class);
        if(params == null) params = new HashMap<>();
        return params;
    }

    /**
     * Get sbe code from cache
     * @return sbe code from cache
     */
    public String getSbeTableFromCache() {
        String sbe = CacheUtils.getObject(user.getProductCode(),getCachedSbeTable() ,String.class);
        if(sbe == null) return sbe = "";
        return sbe;
    }

    /**
     * Clear params in cache
     */
    public void clearParamsInCache() {
        Map<String, SavedSearch> params = new HashMap<>();
        CacheUtils.putObject(user.getProductCode(),getCachedAnswerKey(),params);
    }
}