# Trust or group lead: sequence diagrams

Summaries per school. Never individual children unless a school grants it.

[All roles](README.md) · Traced from the code at commit 93da753 (4 Nov 2026). Errors are problem details: 400 a rule broken, 403 no access, 429 a rate limit. When an endpoint or rule shown here changes, update the diagram in the same pull request.

## Flows

1. [Our schools and spending](#trust-dashboard)

<a id="trust-dashboard"></a>

## Our schools and spending

One row per school with licence status, stages and summary results, plus spending per school.

**Endpoints:** `GET /api/trusts/{id}/dashboard` · `GET /api/trusts/{id}/payments`

```mermaid
sequenceDiagram
  autonumber
  actor L as Trust lead
  participant W as Web app (TrustDashboardPage)
  participant API as DashboardService
  participant AS as AssessmentService
  participant DB as PostgreSQL
  L->>W: Sign in, land on Our schools
  W->>API: GET /api/trusts/{id}/dashboard
  API->>API: requireTrustAccess(trustId)
  API->>DB: schools, licences, renewal dates
  loop each school
    API->>AS: schoolFigures(schoolId)
    AS-->>API: children, share assessed this year, number flagged
  end
  API-->>W: licence status (Active, Renewal due, Free trial, Expired), stages, figures
  W->>API: GET /api/trusts/{id}/payments
  API-->>W: spending per school
  opt school has granted access to children
    L->>W: Open a class
    W->>API: GET /api/classes/{id}/...
    API->>API: requireClassAccess allows TRUST_LEAD only if trustCanSeeChildren
  end
```

**Rules in the code**

- requireTrustAccess: the lead's own trust, or the team.
- Class and child data only where the school has switched on trust access to children.
- Trust leads can never record assessments.
