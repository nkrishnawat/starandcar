package com.resilientechnology.starandcar.event;

import com.resilientechnology.starandcar.record.PropertyDetailVO;

public class PropertyCreatedEvent {
    private final PropertyDetailVO propertyDetailVO;

    public PropertyCreatedEvent(PropertyDetailVO propertyDetailVO) {
        this.propertyDetailVO = propertyDetailVO;
    }

    public PropertyDetailVO getPropertyDetailVO() {
        return propertyDetailVO;
    }
}