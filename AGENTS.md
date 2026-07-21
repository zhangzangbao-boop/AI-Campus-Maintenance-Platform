# AI Campus Maintenance Platform (启云校园智慧维修平台)

## Project Overview

This is a comprehensive campus maintenance and repair management system with AI-powered intelligent analysis capabilities. The platform enables students to report maintenance issues, administrators to assign repair tasks, and maintenance staff to process and complete repairs.

## Architecture

### Backend Services (smart-backend)

Multi-module Spring Boot microservices architecture with Spring Cloud:

```
smart-backend/
├── qiyun-common/         - Shared utilities and common code
├── qiyun-feign-api/      - Feign client interfaces for service-to-service communication
├── qiyun-biz-service/    - Core business service (repair ticket management)
├── qiyun-ai-service/     - AI analysis service (ticket categorization & suggestions)
├── qiyun-gateway/        - Spring Cloud Gateway for unified API routing
```

**Key Technologies:**
- Spring Boot 3.3.4
- Spring Cloud 2023.0.3
- Spring Cloud Alibaba (Nacos) 2023.0.1.2
- Spring Data JPA
- Spring Security + JWT
- OpenFeign for inter-service calls
- MySQL database
- Java 17

### Service Ports & Routing

| Service | Port | Nacos Service Name | API Prefix |
|---------|------|-------------------|------------|
| qiyun-gateway | 8070 | qiyun-gateway | `/api/**` (all routes) |
| qiyun-biz-service | ~8080 | qiyun-biz-service | `/api/**` (default) |
| qiyun-ai-service | 9002 | qiyun-ai-service | `/api/ai/**` |

**Gateway Routing Rules:**
- `/api/ai/**` → qiyun-ai-service (priority 0)
- `/api/**` → qiyun-biz-service (priority 1, fallback)

### Frontend (smart-frontend)

React-based frontend application with Ant Design UI components.

**Key Features:**
- Student portal: submit repair requests, track tickets, provide feedback
- Admin portal: manage tickets, assign staff, view statistics
- Staff portal: view assigned tasks, update status, add repair notes
- AI-powered smart form filling with intelligent analysis

**Key Files:**
- `src/Student/CreateRepairPage.jsx` - Repair submission with AI integration
- `src/services/api.jsx` - Centralized API client with Gateway integration
- `src/services/repairService.js` - Repair-specific API wrappers

## AI Integration

### AI Analysis Workflow

1. **Frontend AI Smart Fill (`CreateRepairPage.jsx`)**
   - User enters problem description in AI text area
   - Clicks "智能识别并填写" button
   - Calls `api.ai.analyzeTicketV2()` → Gateway → qiyun-ai-service
   - AI returns: category, urgency, suggestion, keywords
   - Auto-fills form fields based on AI results

2. **Backend Ticket Creation (`TicketService.java`)**
   - After ticket is saved to database
   - Async call to `AiAnalysisIntegrationService.analyzeAndSave()`
   - Feign client calls qiyun-ai-service
   - Results saved to `ai_ticket_analysis` table
   - Non-blocking: failures don't affect ticket creation

### AI Service Components

**qiyun-ai-service (`AiAnalyzeService.java`):**
- Rule-based keyword matching engine (v1)
- Categories: 空调故障, 管道故障, 电力故障, 网络故障, 家具故障, 门窗故障, 其他故障
- Urgency levels: 紧急 (safety issues), 普通 (functionality issues), 一般 (minor issues)
- Generates maintenance suggestions based on category
- Extracts keywords from description

**qiyun-feign-api (`AiServiceClient.java`):**
- Feign client interface for qiyun-ai-service
- `analyzeTicket()` method returns wrapped response
- `extractResponse()` helper to parse data field

**qiyun-biz-service (`AiAnalysisIntegrationService.java`):**
- Orchestrates AI calls and database persistence
- Maps urgency to priority: 紧急→high, 普通→medium, 一般→low
- Saves analysis results to `ai_ticket_analysis` table

### Database Schema

**ai_ticket_analysis table:**
```sql
id               BIGINT AUTO_INCREMENT PRIMARY KEY
ticket_id        BIGINT           -- Associated repair ticket ID
source_text      TEXT             -- Original problem description
category_key     VARCHAR(50)      -- AI-detected category
location_text    VARCHAR(200)     -- Location information
priority         VARCHAR(20)      -- Priority level (high/medium/low)
urgency          VARCHAR(20)      -- Urgency level (紧急/普通/一般)
suggestion       TEXT             -- AI-generated maintenance suggestion
keywords         TEXT             -- Keywords (JSON array format)
provider         VARCHAR(80)      -- AI provider name
model            VARCHAR(120)     -- AI model version
created_at       DATETIME         -- Analysis timestamp
```

**Database migration:** `smart-backend/qiyun-biz-service/src/main/resources/ai_analysis_enhance.sql`

## Key Business Entities

### RepairTicket

Core entity representing a maintenance request:

**Status Flow:**
```
WAITING_ACCEPT → IN_PROGRESS → RESOLVED → WAITING_FEEDBACK → FEEDBACKED → CLOSED
                 ↓
              REJECTED (can be rejected from WAITING_ACCEPT or IN_PROGRESS)
```

**Key Fields:**
- `student` - User who submitted the ticket
- `staff` - Assigned maintenance worker
- `category` - Repair category (water, electricity, network, etc.)
- `locationText` - Problem location
- `description` - Problem description
- `status` - Current ticket status (TicketStatus enum)
- `priority` - Priority level (high/medium/low)
- `repairNotes` - Notes from maintenance staff
- `processNotes` - Processing notes from admin
- `estimatedCompletionTime` - Expected completion time
- `createdAt`, `assignedAt`, `completedAt`, `closedAt` - Timestamps

### User Roles

- `STUDENT` - Can submit tickets, view own tickets, provide feedback
- `STAFF` - Maintenance workers, can view assigned tasks, update status
- `ADMIN` - Can manage all tickets, assign staff, view statistics

## API Endpoints

### AI Service (qiyun-ai-service)

```
POST /api/ai/analyze-ticket
Request:  { description, location }
Response: { code: 200, message, data: { category, urgency, suggestion, keywords } }
```

### Business Service (qiyun-biz-service)

**Ticket Management:**
```
POST   /api/repair-orders              - Create new ticket
GET    /api/repair-orders/my           - Get user's tickets
GET    /api/repair-orders/{id}         - Get ticket details
DELETE /api/repair-orders/{id}         - Delete ticket (only WAITING_ACCEPT)
POST   /api/repair-orders/{id}/evaluate - Submit feedback/rating

PUT    /api/tasks/{id}/status          - Update ticket status
PUT    /api/admin/repair-orders/{id}/assign - Assign ticket to staff
GET    /api/admin/repair-orders/{id}/recommend-staff - Staff recommendations
```

**User Management:**
```
POST   /api/auth/login                 - User login
POST   /api/auth/register              - User registration
GET    /api/users/me                   - Get current user info
PUT    /api/users/me                   - Update user profile
GET    /api/users?role={role}          - Get users by role
```

**Statistics:**
```
GET    /api/admin/stats/category       - Category distribution
GET    /api/admin/stats/location       - Location distribution
GET    /api/admin/stats/monthly        - Monthly statistics
GET    /api/admin/stats/sla            - SLA timeout warnings
GET    /api/admin/stats/hotspot        - Hotspot analysis
```

## Running the Application

### Prerequisites

- Java 17+
- Maven 3.6+
- MySQL 8.0+
- Node.js 16+ (for frontend)
- Nacos server (for service discovery)

### Backend Startup

1. **Start Nacos server** (default: localhost:8848)
   ```bash
   # Download from https://nacos.io and start in standalone mode
   sh startup.sh -m standalone
   ```

2. **Build all backend modules:**
   ```bash
   cd smart-backend
   mvn clean install -DskipTests
   ```

3. **Start services in order:**
   ```bash
   # Gateway (port 8070)
   cd qiyun-gateway && mvn spring-boot:run

   # AI service (port 9002)
   cd qiyun-ai-service && mvn spring-boot:run

   # Business service (port ~8080)
   cd qiyun-biz-service && mvn spring-boot:run
   ```

### Frontend Startup

```bash
cd smart-frontend
npm install
npm run dev  # Starts on http://localhost:5173
```

**Configure API base URL** in `.env`:
```
VITE_API_BASE_URL=http://localhost:8070/api
```

## Development Guidelines

### Backend Conventions

- **Package structure:**
  - `com.ligong.reportingcenter` - Business service base package
  - `com.qiyun.aiservice` - AI service base package
  - `com.qiyun.feign` - Feign API module

- **Entity naming:** Database columns use snake_case, Java fields use camelCase
- **DTO pattern:** Use DTOs for API responses, not entities directly
- **Exception handling:** Use `BusinessException` for business rule violations
- **Concurrency:** Use pessimistic locks (`findByIdWithLock`) for critical operations

### Frontend Conventions

- **API calls:** Use centralized `api.jsx` client, not direct fetch
- **State management:** React useState/useRef for local state
- **Form handling:** Ant Design Form components with validation rules
- **Error handling:** Always catch API errors and show user-friendly messages

### AI Service Extension

To upgrade from rule-based to real AI:

1. Replace `AiAnalyzeService.analyze()` logic
2. Integrate with actual AI model API (Codex, OpenAI, etc.)
3. Update `model` field in analysis results
4. Keep response schema unchanged for backward compatibility

## Recent Changes

### Latest Commit: AI Analysis Integration (9dd009d)

- Added qiyun-ai-service with rule-based analysis engine
- Integrated AI analysis into ticket creation workflow
- Added frontend AI smart-fill feature
- Created Feign client for service communication
- Enhanced ai_ticket_analysis table schema

### Commit History

```
9dd009d feat: integrate AI analysis service with repair ticket workflow
c159650 feat: integrate frontend with Gateway for unified API access
1531159 fix: change qiyun-gateway default port to 8070
d7cf226 feat: enable Spring Cloud Gateway for qiyun-gateway
43a69e7 feat: enable Nacos discovery for qiyun-ai-service
148a88e feat: enable Nacos discovery for qiyun-biz-service
```

## Testing

### Backend Tests

```bash
cd smart-backend
mvn test  # Run all module tests
```

### Frontend Tests

```bash
cd smart-frontend
npm test
```

### Manual Testing Checklist

1. **AI Analysis:**
   - Enter description → Click smart fill → Verify category/urgency auto-filled
   - Submit ticket → Verify AI modal shows analysis results

2. **Ticket Lifecycle:**
   - Student creates ticket → Admin assigns → Staff updates → Student rates

3. **Gateway Routing:**
   - `/api/ai/**` routes to AI service (check logs)
   - Other `/api/**` routes to business service

## Known Issues & Limitations

1. **AI Service:** Currently uses keyword matching, not real AI model
2. **Image Upload:** Limited to 5 images per ticket, 5MB each
3. **Concurrency:** Optimistic locking may fail on rapid concurrent updates
4. **Notifications:** Basic implementation, no real-time push (WebSocket planned)

## Future Enhancements

- Replace rule-based AI with Codex/OpenAI integration
- Add real-time notification system (WebSocket)
- Implement ticket similarity matching for related issues
- Add maintenance knowledge base with AI drafting
- Enhanced SLA monitoring with auto-escalation
- Mobile app support

## Documentation References

- Spring Cloud Gateway: https://spring.io/projects/spring-cloud-gateway
- Nacos Discovery: https://nacos.io/en-us/
- OpenFeign: https://spring.io/projects/spring-cloud-openfeign
- Ant Design: https://ant.design/

---

*Last updated: 2026-07-10*