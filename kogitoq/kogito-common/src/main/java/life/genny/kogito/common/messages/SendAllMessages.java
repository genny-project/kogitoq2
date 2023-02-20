package life.genny.kogito.common.messages;

import io.quarkus.arc.Arc;
import life.genny.qwandaq.attribute.Attribute;
import life.genny.qwandaq.attribute.EntityAttribute;
import life.genny.qwandaq.constants.Prefix;
import life.genny.qwandaq.entity.BaseEntity;
import life.genny.qwandaq.entity.search.SearchEntity;
import life.genny.qwandaq.entity.search.trait.Filter;
import life.genny.qwandaq.entity.search.trait.Operator;
import life.genny.qwandaq.models.ServiceToken;
import life.genny.qwandaq.utils.AttributeUtils;
import life.genny.qwandaq.utils.SearchUtils;
import org.apache.commons.lang3.StringUtils;
import org.jboss.logging.Logger;

import javax.inject.Inject;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SendAllMessages extends MessageSendingStrategy {

    @Inject
    SearchUtils searchUtils;

    @Inject
    ServiceToken serviceToken;

    @Inject
    AttributeUtils attributeUtils;

    static final Logger log = Logger.getLogger(SendAllMessages.class);
    private final String productCode;
    private final String milestoneCode;
    private final BaseEntity coreBE;

    private final Map<String, String> ctxMap = new HashMap<>();
    private String recipientBECode = null;

    public static final String SBE_MILESTONE_MESSAGES = "SBE_MILESTONE_MESSAGES";
    public static final String NAME = "Fetch All Messages associated with milestone Code";
    public static final String PRI_MILESTONE = "PRI_MILESTONE";
    public static final String PRI_RECIPIENT_LNK = "PRI_RECIPIENT_LNK";
    public static final String PRI_SENDER_LNK = "PRI_SENDER_LNK";
    public static final String RECIPIENT = "RECIPIENT";
    public static final String SENDER = "SENDER";
    public static final String SELF = "SELF";
    public static final String USER = "USER";

    public SendAllMessages(String productCode, String milestoneCode, BaseEntity coreBE) {
        super();
        this.productCode = productCode;
        this.milestoneCode = milestoneCode;
        this.coreBE = coreBE;
    }

    public SendAllMessages(String milestoneCode, String coreBeCode) {
        super();
        this.milestoneCode = milestoneCode;
        this.coreBE = beUtils.getBaseEntity(userToken.getProductCode(), coreBeCode);
        this.productCode = this.coreBE.getRealm();
    }

    @Override
    public void sendMessage() {
        SearchEntity searchEntity = new SearchEntity(SBE_MILESTONE_MESSAGES, NAME)
                .add(new Filter(Attribute.PRI_CODE, Operator.LIKE, "MSG_%"))
                .add(new Filter(PRI_MILESTONE, Operator.LIKE, "%\"" + milestoneCode.toUpperCase() + "\"%"))
                .setPageStart(0)
                .setPageSize(100);

        searchEntity.setRealm(productCode);

        if (searchUtils == null) {
            log.info("searchUtils is null");

        }
        if (searchUtils == null) {

            searchUtils = Arc.container().select(SearchUtils.class).get();
        }
        List<String> messageCodes = searchUtils.searchBaseEntityCodes(searchEntity);

        if (messageCodes != null) {
            log.info("messages : " + messageCodes.size());
            messageCodes.parallelStream().forEach((messageCode) -> {
                log.info("messageCode : " + messageCode);
                BaseEntity message = beUtils.getBaseEntity(messageCode);

                // Determine the recipientBECode
                EntityAttribute recipientLink = beaUtils.getEntityAttribute(message.getRealm(), message.getCode(), PRI_RECIPIENT_LNK);
                if (recipientLink != null) {
                    String recipientLnkValue = recipientLink.getValueString();
                    determineRecipientLnkValueAndUpdateMap(recipientLnkValue);
                } else {
                    log.error("NO " + PRI_RECIPIENT_LNK + " present");
                }

                // Extract all the contexts from the core baseEntity LNKs
                List<EntityAttribute> lnkEAs = beaUtils.getBaseEntityAttributesForBaseEntityWithAttributeCodePrefix(coreBE.getRealm(), coreBE.getCode(), "LNK");
                StringBuilder contextMapStr = new StringBuilder();

                lnkEAs.parallelStream().forEach((ea) -> {
                    String attributeCode = ea.getAttributeCode();
                    Attribute attribute = attributeUtils.getAttribute(attributeCode, true);
                    ea.setAttribute(attribute);
                    String aliasCode = attributeCode.substring("LNK_".length());
                    String aliasValue = ea.getAsString();
                    aliasValue = aliasValue.replace("\"", "").replace("[", "").replace("]", "");
                    contextMapStr.append(aliasCode).append("=").append(aliasValue).append(",");
                    ctxMap.put(aliasCode, aliasValue);
                });

                // Determine the sender
                EntityAttribute senderLnkAttribute = beaUtils.getEntityAttribute(message.getRealm(), message.getCode(), PRI_SENDER_LNK);
                if(senderLnkAttribute != null) {
                    String senderLnkValue = senderLnkAttribute.getValueString();
                    if (senderLnkValue != null) {
                        determineSenderAndUpdateMap(senderLnkValue);
                    } else {
                        log.error("NO " + PRI_SENDER_LNK + " present");
                    }
                } else {
                    log.error("NO " + PRI_SENDER_LNK + " present");
                }

                log.info("Sending Message " + message.getCode() + " to " + recipientBECode + " with ctx="
                        + contextMapStr);

                new SendMessage(message.getCode(), recipientBECode, ctxMap).sendMessage();
            });
        } else {
            log.warn("No messages found for milestoneCode -> " + milestoneCode);
        }

        ctxMap.clear();
    }

    private void determineRecipientLnkValueAndUpdateMap(String recipientLnkValue) {
        if (StringUtils.isNotEmpty(recipientLnkValue)) {
            // check the various formats to get the recipientBECode
            if (recipientLnkValue.startsWith(SELF)) { // The coreBE is the recipient
                ctxMap.put(RECIPIENT, coreBE.getCode());
                recipientBECode = coreBE.getCode();
            } else if (recipientLnkValue.startsWith(Prefix.PER_)) {
                ctxMap.put(RECIPIENT, recipientLnkValue);
                recipientBECode = recipientLnkValue;
            } else {
                // check if it is a LNK path (of the coreBE)
                if (recipientLnkValue.startsWith("LNK_")) {
                    String[] splitStr = recipientLnkValue.split(":");
                    BaseEntity lnkBe = coreBE; // seed
                    for (String s : splitStr) {
                        lnkBe = beUtils.getBaseEntityFromLinkAttribute(lnkBe, s);
                    }
                    ctxMap.put(RECIPIENT, lnkBe.getCode());
                    recipientBECode = lnkBe.getCode();
                }
            }
        }
    }

    private void determineSenderAndUpdateMap(String senderLnkValue) {
        if (senderLnkValue != null) {
            // check the various formats to get the senderBECode
            if (senderLnkValue.startsWith(USER)) { // The user is the sender
                ctxMap.put(SENDER, userToken.getUserCode());
            } else if (senderLnkValue.startsWith(Prefix.PER_)) {
                ctxMap.put(SENDER, senderLnkValue);
            } else {
                // check if it is a LNK path (of the coreBE)
                // "LNK_INTERN"
                if (senderLnkValue.startsWith("LNK_")) {
                    String[] splitStr = senderLnkValue.split(":");
                    BaseEntity lnkBe = coreBE; // seed
                    for (String s : splitStr) {
                        lnkBe = beUtils.getBaseEntityFromLinkAttribute(lnkBe, s);
                    }
                    ctxMap.put(SENDER, lnkBe.getCode());
                }
            }
        }
    }
}
