package com.project.young.productservice.web.message;

import org.springframework.stereotype.Component;

@Component
public class OptionGroupResponseMessageFactory {
    public String groupCreated() { return "Option group created successfully."; }
    public String valueAdded() { return "Option value added successfully."; }
    public String groupUpdated() { return "Option group updated successfully."; }
    public String valueUpdated() { return "Option value updated successfully."; }
    public String groupDeleted() { return "Option group deleted successfully."; }
    public String valueDeleted() { return "Option value deleted successfully."; }
}