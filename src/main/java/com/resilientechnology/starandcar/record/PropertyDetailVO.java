package com.resilientechnology.starandcar.record;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
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
