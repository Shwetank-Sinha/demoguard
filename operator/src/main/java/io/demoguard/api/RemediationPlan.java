package io.demoguard.api;

import java.util.Objects;

public class RemediationPlan {

    public enum Severity { BLOCKING, WARNING }
    public enum PatchFormat { YAML, JSON_PATCH, NONE }

    private String id;
    private Severity severity;
    private String targetKind;
    private String targetName;
    private String summary;
    private String rationale;
    private boolean safeToApply;
    private PatchFormat patchFormat;
    private String patch;

    public RemediationPlan() {
    }

    public RemediationPlan(String id, Severity severity, String targetKind, String targetName,
                           String summary, String rationale, boolean safeToApply,
                           PatchFormat patchFormat, String patch) {
        this.id = Objects.requireNonNull(id);
        this.severity = Objects.requireNonNull(severity);
        this.targetKind = Objects.requireNonNull(targetKind);
        this.targetName = Objects.requireNonNull(targetName);
        this.summary = Objects.requireNonNull(summary);
        this.rationale = Objects.requireNonNull(rationale);
        this.safeToApply = safeToApply;
        this.patchFormat = Objects.requireNonNull(patchFormat);
        this.patch = patch;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public Severity getSeverity() { return severity; }
    public void setSeverity(Severity severity) { this.severity = severity; }
    public String getTargetKind() { return targetKind; }
    public void setTargetKind(String targetKind) { this.targetKind = targetKind; }
    public String getTargetName() { return targetName; }
    public void setTargetName(String targetName) { this.targetName = targetName; }
    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }
    public String getRationale() { return rationale; }
    public void setRationale(String rationale) { this.rationale = rationale; }
    public boolean isSafeToApply() { return safeToApply; }
    public void setSafeToApply(boolean safeToApply) { this.safeToApply = safeToApply; }
    public PatchFormat getPatchFormat() { return patchFormat; }
    public void setPatchFormat(PatchFormat patchFormat) { this.patchFormat = patchFormat; }
    public String getPatch() { return patch; }
    public void setPatch(String patch) { this.patch = patch; }
}
