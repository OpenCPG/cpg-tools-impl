package cpg.covid19.ed.cql;

import static edu.mayo.ontology.taxonomies.clinicalinterrogatives.ClinicalInterrogativeSeries.Is;
import static edu.mayo.ontology.taxonomies.clinicalinterrogatives.ClinicalInterrogativeSeries.Kind_Of;
import static edu.mayo.ontology.taxonomies.clinicalinterrogatives.ClinicalInterrogativeSeries.Last;
import static edu.mayo.ontology.taxonomies.clinicalinterrogatives.ClinicalInterrogativeSeries.Quantity_Of;
import static edu.mayo.ontology.taxonomies.clinicalinterrogatives.ClinicalInterrogativeSeries.Value_Of;
import static edu.mayo.ontology.taxonomies.clinicalinterrogatives.ClinicalInterrogativeSeries.asEnum;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;

import cpg.covid19.ed.SupportedFHIRTypes;
import edu.mayo.ontology.taxonomies.clinicalinterrogatives.ClinicalInterrogative;
import java.util.List;
import org.hl7.fhir.r4.model.Enumerations.FHIRAllTypes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CQLRetrieveGenerator extends AbstractGlossaryCQLGenerator {

  private static final String CONCEPT_PREFIX = "CC";
  private static final String COMMON_PREFIX = "C3F";

  public static final String NAME = "retrieves";
  public static final String VERSION = "0.0.1";
  public static final String FILE_NAME = NAME + "-" + VERSION + ".cql";
  public static final String LIBRARY_NAME = "COVID19_ED_CPG_Retrieves";


  private static final Logger logger = LoggerFactory.getLogger(CQLRetrieveGenerator.class);

  public String toCQL(List<SemanticDataElementInfo> infos) {
    StringBuilder sb = new StringBuilder();

    buildHeader(infos, sb);
    buildRetrieves(sb, infos);

    return sb.toString();
  }


  @Override
  public String getFileName(List<SemanticDataElementInfo> source) {
    return FILE_NAME;
  }

  @Override
  public String getLibraryName(List<SemanticDataElementInfo> source) {
    return LIBRARY_NAME;
  }

  @Override
  public String getVersion(List<SemanticDataElementInfo> source) {
    return VERSION;
  }

  protected void buildHeader(List<SemanticDataElementInfo> infos, StringBuilder sb) {
    sb.append("library ")
        .append(getLibraryName(infos))
        .append(" version '0.0.1'")
        .append("\n\n");
    sb.append("using FHIR version '4.0.1'")
        .append("\n\n");

    sb.append("include FHIRHelpers version '4.0.1'")
        .append("\n");
    sb.append("include CDS_Connect_Commons_for_FHIRv400 version '1.0.2' called " + COMMON_PREFIX)
        .append("\n\n");

    sb.append("include COVID19_ED_CPG_Concepts_Premapped version '0.0.1' called " + CONCEPT_PREFIX)
        .append("\n\n");

    sb.append("include Patient_Demographics_FHIRv400 version '1.0.0'")
        .append("\n");

    sb.append("context Patient")
        .append("\n");
    sb.append("\n");
  }


  protected void buildRetrieves(StringBuilder sb, List<SemanticDataElementInfo> infos) {
    infos.forEach(
        info -> info.fhirResources
            .forEach(res ->  {
              sb.append(toRetrieve(info, res.trim())).append("\n");
              sb.append(toLifts(info, res.trim())).append("\n");
            }));
  }

  protected String toRetrieve(SemanticDataElementInfo info, String resourceType) {
    SupportedFHIRTypes type = SupportedFHIRTypes.fromCode(resourceType);

    String def = "";

    def += needsBlockComment(info);

    def += addSemanticAnnotation(info);

    def += "define "
        + "\""
        + getDefaultRetrieveName(info.label(),resourceType)
        + "\" : " + "\n";

    def +=
        applyTimeFilter(type, info,
            applyValidity(type, info,
                requestCore(type, info)));

    def += needsCloseBlockComment(info);
    return def;
  }


  private String applyTimeFilter(SupportedFHIRTypes type,
      SemanticDataElementInfo info, String retrieve) {
    if (info.temporal == null) {
      return retrieve;
    }

    String lookback = info.temporal.getValue().intValue()
          + " "
          + info.temporal.getUnit();

    String lookbackFunction;
    switch (type) {
      case OBSERVATION:
        lookbackFunction = "ObservationLookBack";
        break;
      case MEDICATIONSTATEMENT:
        lookbackFunction = "MedicationStatementLookBack";
        break;
      case MEDICATIONREQUEST:
        lookbackFunction = "MedicationRequestLookBack";
        break;
      case PROCEDURE:
        lookbackFunction = "ProcedureLookBack";
        break;
      case CONDITION:
        lookbackFunction = "ConditionLookBack";
        break;
      case SERVICEREQUEST:
        lookbackFunction = "ServiceRequestLookBack";
        break;
      case DIAGNOSTICREPORT:
        lookbackFunction = "";
        break;
      case DEVICEREQUEST:
        lookbackFunction = "";
        break;
      case PATIENT:
        return "";
      default:
        throw new UnsupportedOperationException(
            "Cannot create a lookback for " + type.resourceType);
    }

    return COMMON_PREFIX + "." + lookbackFunction + "( " + retrieve + ", " + lookback + ")";
  }

  private String applyValidity(SupportedFHIRTypes type,
      SemanticDataElementInfo info, String retrieve) {
    // the kind of filter should by implied by the ontology's 'known context' axis
    switch (type) {
      case OBSERVATION:
        return COMMON_PREFIX + ".Verified( " + retrieve + " )";
      case MEDICATIONSTATEMENT:
        return COMMON_PREFIX + ".ActiveMedicationStatement( " + retrieve + " )";
      case MEDICATIONREQUEST:
        return COMMON_PREFIX + ".ActiveMedicationRequest( " + retrieve + " )";
      case PROCEDURE:
        return COMMON_PREFIX + ".ProcedurePerformance( " + retrieve + " )";
      case CONDITION:
        return
            COMMON_PREFIX + ".Confirmed( "
                + COMMON_PREFIX + ".ActiveOrRecurring( " + retrieve + " ) ) ";
      case SERVICEREQUEST:
        return COMMON_PREFIX + ".ServiceRequestActiveOrCompleted( " + retrieve + " )";
      case DIAGNOSTICREPORT:
        // TODO FIXME there is not a helper for DxReport
        return fixme(info) + COMMON_PREFIX + ".Verified( " + retrieve + " )";
      case DEVICEREQUEST:
        // TODO FIXME there is not a helper for DeviceRequest
        return fixme(info) + COMMON_PREFIX + ".ServiceRequestActiveOrCompleted( " + retrieve + " )";
      case PATIENT:
        return "";
      default:
        throw new UnsupportedOperationException("Cannot create a retrieve for " + type.resourceType);
    }
  }


  private String requestCore(SupportedFHIRTypes fhir, SemanticDataElementInfo info) {
    return  "[ " + fhir.resourceType
        + " : "
        + CONCEPT_PREFIX + ".\"" + info.dataElement.trim() + "\""
        + " ]";
  }

  private String addSemanticAnnotation(SemanticDataElementInfo info) {
    return  "//"
        + getSituationUri(info)
        + "\n";
  }











  protected String toLifts(SemanticDataElementInfo info, String resourceType) {
    StringBuilder def = new StringBuilder();
    List<ClinicalInterrogative> interrogatives = getAdmissibleInterrogatives(resourceType);
    String baseExpr =  getDefaultRetrieveName(info.label(), resourceType);

    interrogatives.forEach(interr -> {
      def.append(needsBlockComment(info));
      def.append("define " + "\"")
          .append(getDefaultLiftName(baseExpr, interr.getLabel()))
          .append("\" : ").append("\n\t");
      switch (asEnum(interr)) {
        case Is:
          def.append("Exists( \"").append(baseExpr).append("\" ) ").append("\n");
          break;
        case Last:
          def.append("Last( \"").append(baseExpr).append("\" ) ").append("\n");
          break;
        case Kind_Of:
          def.append("\"").append(getDefaultLiftName(baseExpr, Last.getLabel())).append("\".code ").append("\n");
          break;
        case Value_Of:
          def.append("\"").append(getDefaultLiftName(baseExpr, Last.getLabel())).append("\".value ").append("\n");
          break;
        case Quantity_Of:
          def.append("\"").append(getDefaultLiftName(baseExpr, Last.getLabel())).append("\".value ").append("\n");
          break;
        default:
          throw new UnsupportedOperationException();
      }
      def.append(needsCloseBlockComment(info));
      def.append("\n");
    });

    return def.toString();
  }

  protected List<ClinicalInterrogative> getAdmissibleInterrogatives(String resourceType) {
    switch (FHIRAllTypes.fromCode(resourceType)) {
      case OBSERVATION:
        return asList(Is, Last, Kind_Of, Value_Of, Quantity_Of);
      case CONDITION:
        return asList(Is, Last, Kind_Of);
      case PROCEDURE:
        return asList(Is, Last, Kind_Of);
      case PATIENT:
        return emptyList();
      default:
        return singletonList(Is);
    }
  }


  public static String getDefaultRetrieveName(String label, String resourceType) {
    return label + " - " + resourceType;
  }

  public static String getDefaultLiftName(String baseExpr, String label) {
    return baseExpr + " - " + label;
  }



}
