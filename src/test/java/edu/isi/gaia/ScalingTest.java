package edu.isi.gaia;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import com.google.common.collect.ImmutableSet;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.riot.RDFFormat;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.SKOS;
import org.apache.jena.vocabulary.XSD;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static edu.isi.gaia.AIFUtils.*;

public class ScalingTest {

    private static final String LDC_NS = "https://tac.nist.gov/tracks/SM-KBP/2018/LdcAnnotations#";
    private Model model;
    private Resource system;

    // Beginning sizes of data, about what is in T101
    private int entityCount = 1000;
    private int eventCount = 300;
    private int relationCount = 200;
    // T101 has 3000 assertions, but 1500 of them are type assertions associated with entity and events, so
    // do not count them.
    private int assertionCount = 1500;

    private int entityIndex = 1;
    private int eventIndex = 1;
    private int relationIndex = 1;
    private int assertionIndex = 1;

    // Utility values, so that we can easily create random things
    final static String abc = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    final Resource[] entityTypes = SeedlingOntologyMapper.ENTITY_TYPES.toArray(new Resource[0]);

    final SeedlingOntologyMapper ontologyMapping = new SeedlingOntologyMapper();

    private final String putinDocumentEntityUri = getUri("E781167.00398");
    private final String putinElectedDocumentEventUri = getUri("V779961.00010");
    private final String russiaDocumentEntityUri = getUri("E779954.00004");

    private final Random r = new Random();
    private List<Resource> entityResourceList = new ArrayList<>();

    /**
     * Main function.  Call with no arguments
     */
    public static void main(String[] args) {
        ScalingTest scalingTest = new ScalingTest();
        scalingTest.runtest();
    }

    private void runtest() {
        // prevent too much logging from obscuring the Turtle examples which will be printed
        ((Logger) org.slf4j.LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME)).setLevel(Level.INFO);

        setup();

        for (int ii = 0; ii < entityCount; ii++) {
            addEntity();
        }
        for (int ii = 0; ii < eventCount; ii++) {
            addEvent();
        }

        dumpAndAssertValid("scalingdata");
    }

    private void setup() {
        model = createModel();
        system = makeSystemWithURI(model, getTestSystemUri());
    }

    private void addEntity() {

        // Add an entity
        Resource entityResource = makeEntity(model, getEntityUri(), system);
        entityResourceList.add(entityResource);

        // sometimes add hasName, textValue, or numericValue, NOTE:  This does not check type!!!!
        double rand = r.nextDouble();
        if (rand < 0.15) {
            markName(entityResource, getRandomString(5));
        } else if (rand < 0.3) {
            markTextValue(entityResource, getRandomString(7));
        } else if (rand < 0.4) {
            markNumericValueAsDouble(entityResource, r.nextDouble());
        }

        // Set the type
        Resource typeToUse = entityTypes[r.nextInt(entityTypes.length)];
        Resource typeAssertion = markType(model, getAssertionUri(), entityResource,
                typeToUse, system, 1.0);

        addJustificationAndPrivateData(typeAssertion);
    }

    private void addEvent() {
        // Add an event
        Resource eventResource = makeEvent(model, getEventUri(), system);

        // Set the type
        String eventTypeString = EVENT_TYPES[r.nextInt(EVENT_TYPES.length)];
        Resource typeResource = ontologyMapping.eventType(eventTypeString);
        Resource typeAssertion = markType(model, getAssertionUri(), eventResource, typeResource, system, 1.0);

        addJustificationAndPrivateData(typeAssertion);

        // Make two arguments
        for (int ii = 0; ii < 2; ii++) {
            Resource argument = markAsArgument(model, eventResource,
                    ontologyMapping.eventArgumentTypeNotLowercase(eventTypeString + getRandomSuffix()),
                    getRandomEntity(), system, 0.785, getAssertionUri());
            addJustificationAndPrivateData(argument);
        }
    }

    private void addJustificationAndPrivateData(Resource resource) {
        String docId = getRandomDocId();

        // Justify the type assertion
        markTextJustification(model, ImmutableSet.of(resource), docId, 1029, 1033, system, 0.973);

        // Add some private data
        markPrivateData(model, resource, "{ 'provenance' : '" + docId + "' }", system);
    }

    // we dump the test name and the model in Turtle format so that whenever the user
    // runs the tests, they will also get the examples
    private void dumpAndAssertValid(String testName) {
        System.out.println("\n\n" + testName + "\n\n");
        try {
            // RDFDataMgr.write(System.out, model, RDFFormat.TURTLE_PRETTY);
            RDFDataMgr.write(Files.newOutputStream(Paths.get(testName)), model, RDFFormat.TURTLE_PRETTY);
        } catch (Exception e) {
            System.err.println("Unable to write to file " + testName + " " + e.getMessage());
            e.printStackTrace();
        }
    }

    private Model createModel() {
        final Model model = ModelFactory.createDefaultModel();
        // adding namespace prefixes makes the Turtle output more readable
        model.setNsPrefix("rdf", RDF.uri);
        model.setNsPrefix("xsd", XSD.getURI());
        model.setNsPrefix("aida", AidaAnnotationOntology.NAMESPACE);
        model.setNsPrefix("ldcOnt", SeedlingOntologyMapper.NAMESPACE_STATIC);
        model.setNsPrefix("ldc", LDC_NS);
        model.setNsPrefix("skos", SKOS.uri);
        return model;
    }

    private static String getUri(String localName) {
        return LDC_NS + localName;
    }

    private String getEntityUri() {
        return getUri("entity-" + entityIndex++);
    }

    private String getEventUri() {
        return getUri("event-" + eventIndex++);
    }

    private String getRelationUri() {
        return getUri("relation-" + relationIndex++);
    }

    private String getAssertionUri() {
        return getUri("assertion-" + assertionIndex++);
    }

    private String getTestSystemUri() {
        return getUri("testSystem");
    }

    private String getRandomDocId() {
        String s = "";
        if (r.nextBoolean()) {
            s += "IC";
        } else {
            s += "HC";
        }
        s += "00";
        s += "" + (r.nextInt(1000));
        s += abc.charAt(r.nextInt(abc.length()));
        s += abc.charAt(r.nextInt(abc.length()));
        s += abc.charAt(r.nextInt(abc.length()));
        return s;
    }

    private String getRandomString(int length) {
        String s = "";
        for (int ii = 0; ii < length; ii++) {
            s += abc.charAt(r.nextInt(abc.length()));
        }
        return s;
    }

    private Resource getRandomEntity() {
        return entityResourceList.get(r.nextInt(entityResourceList.size()));
    }


    /**
     * Return a string representing an argument
     */
    private String getRandomSuffix() {
        String s = "_" + ROLES[r.nextInt(ROLES.length)];
        return s;
    }


    private final String[] EVENT_TYPES = {
            "Business.DeclareBankruptcy", "Business.End", "Business.Merge", "Business.Start",
            "Conflict.Attack", "Conflict.Demonstrate",
            "Contact.Broadcast", "Contact.Contact", "Contact.Correspondence", "Contact.Meet",
            "Existence.DamageDestroy",
            "Government.Agreements", "Government.Legislate", "Government.Spy", "Government.Vote",
            "Inspection.Artifact", "Inspection.People",
            "Justice.Acquit", "Justice.Appeal", "Justice.ArrestJail", "Justice.ChargeIndict", "Justice.Convict",
            "Justice.Execute", "Justice.Extradite", "Justice.Fine", "Justice.Investigate", "Justice.Pardon",
            "Justice.ReleaseParole", "Justice.Sentence", "Justice.Sue", "Justice.TrialHearing",
            "Life.BeBorn", "Life.Die", "Life.Divorce", "Life.Injure", "Life.Marry",
            "Manufacture.Artifact",
            "Movement.TransportArtifact", "Movement.TransportPerson",
            "Personnel.Elect", "Personnel.EndPosition", "Personnel.Nominate", "Personnel.StartPosition",
            "Transaction.Transaction", "Transaction.TransferControl", "Transaction.TransferMoney",
            "Transaction.TransferOwnership"};

    private final String[] ROLES = {"Attacker", "Instrument", "Place", "Target", "Time", "Broadcaster",
            "Place", "Time", "Participant", "Place", "Participant", "Time",
            "Participant", "Affiliate", "Affiliation", "Affiliation", "Person",
            "Entity", "Sponsor", "Defendant", "Prosecutor", "Adjudicator",
            "Defendant", "Agent", "Instrument", "Victim", "Artifact",
            "Manufacturer", "Agent", "Artifact", "Destination", "Instrument",
            "Origin", "Time", "Agent", "Destination", "Instrument", "Origin",
            "Person", "Employee", "Organization", "Person", "Entity", "Place",
            "Beneficiary", "Giver", "Recipient", "Thing", "Time"};

}