package life.genny.kogito.custom.askquestions.config;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.json.bind.Jsonb;
import javax.json.bind.JsonbBuilder;
import javax.persistence.EntityManager;

import org.apache.commons.lang3.StringUtils;
import org.jboss.logging.Logger;
import org.kie.kogito.internal.process.runtime.KogitoWorkItem;
import org.kie.kogito.internal.process.runtime.KogitoWorkItemHandler;
import org.kie.kogito.internal.process.runtime.KogitoWorkItemManager;

import life.genny.qwandaq.entity.BaseEntity;
import life.genny.qwandaq.entity.SearchEntity;
import life.genny.qwandaq.models.GennyToken;
import life.genny.qwandaq.utils.BaseEntityUtils;
import life.genny.qwandaq.utils.CacheUtils;
import life.genny.qwandaq.utils.KeycloakUtils;
import life.genny.qwandaq.utils.QuestionUtils;

public class AskQuestionsWorkItemHandler implements KogitoWorkItemHandler {

    private static final Logger log = Logger.getLogger(AskQuestionsWorkItemHandler.class);
    public EntityManager entityManager;

    Jsonb jsonb = JsonbBuilder.create();

    @Override
    public void executeWorkItem(KogitoWorkItem workItem, KogitoWorkItemManager manager) {
        log.info("Hello from the custom AskQuestions work item .");
        QuestionUtils questionUtils = new QuestionUtils();

        String qc = null;
        // log.info("Passed parameters:");

        // Printing task’s parameters, it will also print
        // our value we pass to the task from the process
        for (String parameter : workItem.getParameters().keySet()) {
            if ("questionCode".equals(parameter)) {
                qc = (String) workItem.getParameters().get(parameter);
                System.out.println("QuestionCode=" + qc);
            } else {
                System.out.println(parameter + " = " + workItem.getParameters().get(parameter));
            }
        }

        GennyToken serviceToken = KeycloakUtils.getToken("https://keycloak.gada.io", "internmatch", "admin-cli",
                null, "service", System.getenv("GENNY_SERVICE_PASSWORD"));
        log.info("ServiceToken = " + serviceToken.getToken());
        BaseEntityUtils beUtils = new BaseEntityUtils(serviceToken, serviceToken);

        SearchEntity searchBE = new SearchEntity("SBE_TESTUSER", "test user Search")
                .addSort("PRI_CREATED", "Created", SearchEntity.Sort.DESC)
                .addFilter("PRI_CODE", SearchEntity.StringFilter.EQUAL, "PER_086CDF1F-A98F-4E73-9825-0A4CFE2BB943") // testuser
                .addColumn("PRI_CODE", "Code");

        searchBE.setRealm("internmatch");

        searchBE.setPageStart(0);
        Integer pageSize = 1;
        searchBE.setPageSize(pageSize);

        List<BaseEntity> recipients = beUtils.getBaseEntitys(searchBE); // load 100 at a time
        if (recipients != null) {
            BaseEntity recipient = null;
            if (recipients.size() > 0) {

                recipient = recipients.get(0);
                log.info("Recipient = " + recipient.getCode());
            }

            Jsonb jsonb = JsonbBuilder.create();

            log.info("Recipient=" + jsonb.toJson(recipient));

            String userCode = recipient.getCode();
            String username = "testuser@gada.io";
            log.info("usercode = " + userCode + " usernamer=[" + username + "]");
            String userTokenStr = (String) CacheUtils.readCache("internmatch", "TOKEN:" + userCode);
            log.info("fetching from internmatch cache [" + "TOKEN:" + userCode + "]");
            if (StringUtils.isBlank(userTokenStr)) {
                log.error("Make sure you log into your target genny system as testuser@gada.io ");
            } else {

                log.info("usercode = " + userCode + " CacheJsonStr=[" + userTokenStr + "]");
                GennyToken userToken = new GennyToken("userToken", userTokenStr);
                log.info(
                        "User " + username + " is logged in! " + userToken.getAdecodedTokenMap().get("session_state"));

                // Fetch the Questions

                // Create the Ask

                questionUtils.sendQuestions(recipient, userToken);

                // Set up a UserTask
            }

        } else {
            log.error("No recipients matched search");
        }

        Map<String, Object> results = new HashMap<String, Object>();
        results.put("Result", "Message Returned from Work Item Handler");
        // Don’t forget to finish the work item otherwise the process
        // will be active infinitely and never will pass the flow
        // to the next node.
        manager.completeWorkItem(workItem.getStringId(), results);
    }

    @Override
    public void abortWorkItem(KogitoWorkItem workItem, KogitoWorkItemManager manager) {
        // log.error("Error happened in the custom work item definition.");
    }
}
