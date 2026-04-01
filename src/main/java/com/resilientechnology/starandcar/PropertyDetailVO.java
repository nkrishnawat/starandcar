package com.resilientechnology.starandcar;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class PropertyDetailVO {
        String address;
        String email;
        String phoneno;
        String description;
        String notes;
        Long count;
}
