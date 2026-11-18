package com.codebuzzers.codebuzzcore.common;

public final class Codes {

    private Codes() {
    }

    public static final class Permission {
        public static final String LEAD_VIEW_OWN = "LEAD_VIEW_OWN";
        public static final String LEAD_VIEW_TEAM = "LEAD_VIEW_TEAM";
        public static final String LEAD_VIEW_COMPANY = "LEAD_VIEW_COMPANY";
        public static final String LEAD_UPDATE = "LEAD_UPDATE";
        public static final String LEAD_ASSIGN = "LEAD_ASSIGN";
        public static final String LEAD_EXPORT = "LEAD_EXPORT";
        public static final String TASK_MANAGE = "TASK_MANAGE";
        public static final String META_MANAGE = "META_MANAGE";
        public static final String CAMPAIGN_VIEW = "CAMPAIGN_VIEW";
        public static final String USER_MANAGE = "USER_MANAGE";
        public static final String TEAM_MANAGE = "TEAM_MANAGE";
        public static final String BILLING_VIEW = "BILLING_VIEW";
        public static final String AUDIT_VIEW = "AUDIT_VIEW";
        public static final String REPORT_VIEW = "REPORT_VIEW";
        public static final String SUPPORT_VIEW = "SUPPORT_VIEW";
        public static final String SUPPORT_MANAGE = "SUPPORT_MANAGE";
        public static final String PLATFORM_TENANT_MANAGE = "PLATFORM_TENANT_MANAGE";

        private Permission() {
        }
    }

    public static final class Feature {
        public static final String LEAD_INGESTION = "lead_ingestion";
        public static final String ROUND_ROBIN = "round_robin";
        public static final String TASKS = "tasks";
        public static final String PERFORMANCE = "performance";
        public static final String CAMPAIGN_ANALYTICS = "campaign_analytics";
        public static final String CSV_EXPORT = "csv_export";

        private Feature() {
        }
    }

    public static final class LeadStatus {
        public static final String NEW = "NEW";
        public static final String ASSIGNED = "ASSIGNED";
        public static final String CONTACTED = "CONTACTED";
        public static final String FOLLOW_UP = "FOLLOW_UP";
        public static final String WON = "WON";
        public static final String LOST = "LOST";
        public static final String SPAM = "SPAM";
        public static final String DUPLICATE = "DUPLICATE";

        private LeadStatus() {
        }

        public static boolean terminal(String status) {
            return WON.equals(status) || LOST.equals(status) || SPAM.equals(status) || DUPLICATE.equals(status);
        }

        public static boolean open(String status) {
            return NEW.equals(status) || ASSIGNED.equals(status) || CONTACTED.equals(status) || FOLLOW_UP.equals(status);
        }
    }

    public static final class UserStatus {
        public static final String INVITED = "INVITED";
        public static final String ACTIVE = "ACTIVE";
        public static final String DISABLED = "DISABLED";
        public static final String TERMINATED = "TERMINATED";

        private UserStatus() {
        }
    }

    public static final class TenantStatus {
        public static final String ACTIVE = "ACTIVE";
        public static final String SUSPENDED = "SUSPENDED";
        public static final String PENDING = "PENDING";
        public static final String DELETED = "DELETED";

        private TenantStatus() {
        }
    }

    public static final class JobType {
        public static final String INGEST_LEAD = "INGEST_LEAD";
        public static final String SYNC_CAMPAIGNS = "SYNC_CAMPAIGNS";

        private JobType() {
        }
    }

    public static final class JobStatus {
        public static final String PENDING = "PENDING";
        public static final String RUNNING = "RUNNING";
        public static final String DONE = "DONE";
        public static final String DEAD = "DEAD";

        private JobStatus() {
        }
    }

    public static final class WebhookStatus {
        public static final String RECEIVED = "RECEIVED";
        public static final String DONE = "DONE";
        public static final String DEAD = "DEAD";

        private WebhookStatus() {
        }
    }

    public static final class Source {
        public static final String ANDROID = "ANDROID";
        public static final String PLATFORM = "PLATFORM";
        public static final String WORKER = "WORKER";
        public static final String WEBHOOK = "WEBHOOK";

        private Source() {
        }
    }

    public static final class Activity {
        public static final String CALL = "CALL";
        public static final String NOTE = "NOTE";
        public static final String STATUS_CHANGE = "STATUS_CHANGE";
        public static final String ASSIGNMENT = "ASSIGNMENT";
        public static final String SYSTEM = "SYSTEM";

        private Activity() {
        }
    }

    public static final String QUALIFYING_CONTACT = "CONNECTED,MANUAL_CONTACT,WHATSAPP_REPLY";
}
