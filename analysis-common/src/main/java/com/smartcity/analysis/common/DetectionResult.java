package com.smartcity.analysis.common;

import com.smartcity.common.model.AlertType;

public record DetectionResult(AlertType alertType, String message, String recommendation) {
}
