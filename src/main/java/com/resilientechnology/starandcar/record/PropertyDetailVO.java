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
        @Email
        String email;
        String phoneno;
        @Size(max = 2000, message = "Max 2000 char allowed.")
        String description;
        @Size(max = 2000, message = "Max 2000 char allowed.")
        String notes;
        Long count;
}
