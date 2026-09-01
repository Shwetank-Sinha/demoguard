{{- define "demoguard.name" -}}
{{- default .Chart.Name .Values.nameOverride | trunc 63 | trimSuffix "-" }}
{{- end }}

{{- define "demoguard.fullname" -}}
{{- if .Values.fullnameOverride }}
{{- .Values.fullnameOverride | trunc 63 | trimSuffix "-" }}
{{- else }}
{{- $name := default .Chart.Name .Values.nameOverride }}
{{- if contains $name .Release.Name }}
{{- .Release.Name | trunc 63 | trimSuffix "-" }}
{{- else }}
{{- printf "%s-%s" .Release.Name $name | trunc 63 | trimSuffix "-" }}
{{- end }}
{{- end }}
{{- end }}

{{- define "demoguard.chart" -}}
{{- printf "%s-%s" .Chart.Name .Chart.Version | replace "+" "_" | trunc 63 | trimSuffix "-" }}
{{- end }}

{{- define "demoguard.labels" -}}
helm.sh/chart: {{ include "demoguard.chart" . }}
app.kubernetes.io/part-of: {{ include "demoguard.name" . }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
app.kubernetes.io/instance: {{ .Release.Name }}
{{- end }}

{{- define "demoguard.operatorName" -}}
{{- printf "%s-operator" (include "demoguard.fullname" .) | trunc 63 | trimSuffix "-" }}
{{- end }}

{{- define "demoguard.dashboardName" -}}
{{- printf "%s-dashboard" (include "demoguard.fullname" .) | trunc 63 | trimSuffix "-" }}
{{- end }}

{{- define "demoguard.operatorServiceAccountName" -}}
{{- default (include "demoguard.operatorName" .) .Values.operator.serviceAccount.name }}
{{- end }}

{{- define "demoguard.dashboardServiceAccountName" -}}
{{- default (include "demoguard.dashboardName" .) .Values.dashboard.serviceAccount.name }}
{{- end }}
