# The OHS libraries map FHIR model classes to FHIR names by class name, e.g. Engine's
# getResourceType() does ResourceType.valueOf(Patient::class.simpleName), workflow's
# resourceTypeName(), and Data Capture's answer validator compares names like "Boolean" for
# QuestionnaireResponse.Item.Answer.Value.Boolean. So R4 model class names (nested ones too) must
# survive R8. Unused classes are still removed; only the names of the kept ones stay.
-keepnames class dev.ohs.fhir.model.r4.** { *; }
-keepattributes InnerClasses,EnclosingMethod
