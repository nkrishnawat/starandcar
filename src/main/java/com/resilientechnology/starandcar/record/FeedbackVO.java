package com.resilientechnology.starandcar.record;

import jakarta.validation.constraints.Size;
import lombok.*;

@Getter
@Setter
@Builder
@EqualsAndHashCode
@ToString
public class FeedbackVO {
    @Size(min = 0, max = 500, message = "Max 500 chars allowed.")
    String description;
}