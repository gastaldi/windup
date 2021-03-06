package org.jboss.windup.rules.apps.xml.legacy;

import org.jboss.windup.config.WindupRuleProvider;
import org.jboss.windup.config.metadata.RuleMetadata;
import org.jboss.windup.config.operation.Iteration;
import org.jboss.windup.graph.GraphContext;
import org.jboss.windup.reporting.config.Classification;
import org.jboss.windup.reporting.config.Hint;
import org.jboss.windup.rules.apps.xml.condition.XmlFile;
import org.ocpsoft.rewrite.config.Configuration;
import org.ocpsoft.rewrite.config.ConfigurationBuilder;
import org.ocpsoft.rewrite.context.Context;

/**
 * @author <a href="mailto:mbriskar@gmail.com">Matej Briškár</a>
 * 
 */
public class XmlEjbConfig extends WindupRuleProvider
{
    @Override
    public void enhanceMetadata(Context context)
    {
        super.enhanceMetadata(context);
        context.put(RuleMetadata.CATEGORY, "XML");
    }

    // @formatter:off
    @Override
    public Configuration getConfiguration(GraphContext context)
    {
        Configuration configuration = ConfigurationBuilder
                    .begin()
                    .addRule()
                    .when(XmlFile.matchesXpath("/j2e:ejb-jar | /jee:ejb-jar | /ejb-jar").namespace("jee", "http://java.sun.com/xml/ns/javaee").namespace("j2e", "http://java.sun.com/xml/ns/j2ee").as("ejb")
                                .or(XmlFile.from("ejb").matchesXpath("/ejb-jar//message-driven//ejb-name | /j2e:ejb-jar//j2e:message-driven//j2e:ejb-name | /jee:ejb-jar//jee:message-driven//jee:ejb-name").namespace("jee", "http://java.sun.com/xml/ns/javaee").namespace("j2e", "http://java.sun.com/xml/ns/j2ee").as("MDB"))
                                .or(XmlFile.from("ejb").matchesXpath("/ejb-jar//session//ejb-name | /j2e:ejb-jar//j2e:session//j2e:ejb-name | /jee:ejb-jar//jee:session//jee:ejb-name").namespace("jee", "http://java.sun.com/xml/ns/javaee").namespace("j2e", "http://java.sun.com/xml/ns/j2ee").as("sessionEJB"))
                                .or(XmlFile.from("ejb").matchesXpath("/ejb-jar//entity//ejb-name | /j2e:ejb-jar//j2e:entity//j2e:ejb-name | /jee:ejb-jar//jee:entity//jee:ejb-name").namespace("jee", "http://java.sun.com/xml/ns/javaee").namespace("j2e", "http://java.sun.com/xml/ns/j2ee").as("entityEJB"))
                                .or(XmlFile.from("ejb").matchesXpath("//*[local-name()='ejb-relation']/*[local-name()='ejb-relationship-role'][2]/*[local-name()='ejb-relationship-role-name']").as("ejbRelationship"))
                                .or(XmlFile.from("ejb").withDTDPublicId("Sun Microsystems, Inc.//DTD Enterprise JavaBeans 2..").as("ejb2"))
                                .or(XmlFile.from("ejb").withDTDPublicId("Sun Microsystems, Inc.//DTD Enterprise JavaBeans 1..").as("ejb1")))
                    .perform(Iteration.over("ejb").perform(Classification.as("EJB XML")).endIteration()
                             .and(Iteration.over("MDB").perform(Classification.as("EJB - MDB")).endIteration())
                             .and(Iteration.over("sessionEJB").perform(Hint.withText("EJB - Session")).endIteration())
                             .and(Iteration.over("entityEJB").perform(Hint.withText("EJB - Entity")).endIteration())
                             .and(Iteration.over("ejbRelationship").perform(Hint.withText("EJB Relationship")).endIteration())
                             .and(Iteration.over("ejb1").perform(Classification.as("EJB 1.x")).endIteration())
                             .and(Iteration.over("ejb2").perform(Classification.as("EJB 2.x")).endIteration()));
        return configuration;
    }
    // @formatter:on
}