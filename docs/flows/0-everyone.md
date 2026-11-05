# Everyone: sequence diagrams

Signing in, accounts, paying by card and the country settings. Used by every role.

[All roles](README.md) · Traced from the code at commit 93da753 (4 Nov 2026). Errors are problem details: 400 a rule broken, 403 no access, 429 a rate limit. When an endpoint or rule shown here changes, update the diagram in the same pull request.

## Flows

1. [Sign in and every request after it](#sign-in)
2. [Staff invite, set password and reset](#invite)
3. [Pay by card (Stripe Checkout)](#pay-card)
4. [The country settings](#country)

<a id="sign-in"></a>

## Sign in and every request after it

How a token is issued, then checked on every call, before AccessPolicy decides what the caller may see.

**Endpoints:** `POST /api/auth/login` · `GET /api/auth/me`

```mermaid
sequenceDiagram
  autonumber
  actor U as Any user
  participant W as Web app or parent app
  participant API as AuthController
  participant RL as RateLimiter
  participant DB as PostgreSQL
  U->>W: Email and password
  W->>API: POST /api/auth/login
  API->>RL: check login-ip and login per email
  alt too many attempts
    RL-->>W: 429 Too many requests
  end
  API->>DB: findByEmailIgnoreCase(email)
  API->>API: BCrypt match (dummy hash if no user)
  alt no user, wrong password or switched off
    API-->>W: 401 Email or password not recognised
  else match
    API->>RL: reset login counter for this email
    API-->>W: JWT (a working day, 30 days for parents) and current user
    W->>W: Store token, go to the role's home page
  end
  Note over W,DB: Every call after sign-in
  W->>API: Any /api request with Bearer JWT
  API->>API: Spring Security validates JWT signature and expiry
  API->>DB: ActiveUserFilter checks account still enabled (cached 1 min)
  API->>API: AccessPolicy.require... for the class, school, trust, child or family
  alt not allowed
    API-->>W: 403 problem detail
  else allowed
    API-->>W: 200 data
  end
```

**Rules in the code**

- Unknown emails are still checked against a dummy hash, so a wrong email takes as long as a wrong password.
- Login is limited to 50 per address and 10 per email in 15 minutes.
- A switched-off account loses access within a minute, even with a valid token.

<a id="invite"></a>

## Staff invite, set password and reset

Heads, the team and trust setup create accounts with no usable password. The person sets one from an emailed link. Parents register themselves.

**Endpoints:** `POST /api/schools/{id}/staff` · `POST /api/auth/password` · `POST /api/auth/password-reset` · `POST /api/auth/register`

```mermaid
sequenceDiagram
  autonumber
  actor H as Head or team
  actor N as New staff member
  participant W as Web app
  participant API as API
  participant PS as PasswordService
  participant DB as PostgreSQL
  participant M as Mailer (SMTP)
  H->>W: Add staff (name, email, Teacher or Head)
  W->>API: POST /api/schools/{id}/staff
  API->>API: AccessPolicy.requireSchoolAccess
  API->>DB: email already used?
  alt exists
    API-->>W: 409 An account with this email already exists
  else new
    API->>DB: save AppUser with random password
    API->>PS: sendInvite(user, invitedBy)
    PS->>DB: save one-time token (7 days)
    PS->>M: Your Motor Movers account, link to /reset-password?token
    API-->>W: StaffMember row
  end
  M-->>N: Invite email
  N->>W: Open link, choose password
  W->>API: POST /api/auth/password (token, password)
  API->>PS: check token unused and in date, length 10+
  PS->>DB: set password hash, spend token
  API-->>W: email
  N->>W: Sign in as usual
  Note over N,M: Forgotten password
  N->>W: Forgot password
  W->>API: POST /api/auth/password-reset
  API->>PS: requestReset(email)
  PS->>M: Reset link (1 hour), only if the account exists
  API-->>W: 200 either way
```

**Rules in the code**

- Invite links work once, for 7 days. Reset links last 1 hour.
- Passwords are at least 10 characters.
- Register always creates a PARENT account (10 an hour per address).

<a id="pay-card"></a>

## Pay by card (Stripe Checkout)

A head paying a school invoice, or a practitioner or parent paying their own, goes to Stripe. Stripe's webhook marks the payment paid.

**Endpoints:** `GET /api/payments/online` · `POST /api/payments/{id}/checkout` · `POST /api/stripe/webhook`

```mermaid
sequenceDiagram
  autonumber
  actor P as Payer (head, practitioner or parent)
  participant W as Web app
  participant API as OnlinePaymentService
  participant DB as PostgreSQL
  participant S as Stripe
  W->>API: GET /api/payments/online
  API-->>W: enabled true or false
  P->>W: Pay by card on a DUE payment
  W->>API: POST /api/payments/{id}/checkout
  API->>DB: load payment
  alt not the payer, or not DUE
    API-->>W: 403 or 400
  else ok
    API->>S: create Checkout Session (amount in the minor unit, the deployment's currency, paymentId in metadata)
    S-->>API: checkout URL
    API-->>W: url
    W->>S: redirect
    P->>S: card details
    S-->>W: back to the payments page
  end
  S->>API: POST /api/stripe/webhook (Stripe-Signature)
  API->>API: StripeSignature.valid(payload, secret, now)
  alt bad signature
    API-->>S: 400 This call is not signed by Stripe
  else checkout.session.completed and paid
    API->>DB: payment.paidOnline(today, payment_intent)
    API-->>S: 200
  end
```

**Rules in the code**

- Only the payer can start checkout, and only for a payment that is DUE.
- The webhook is refused unless Stripe's signature is valid.
- A bank debit still on its way waits for async_payment_succeeded.

<a id="country"></a>

## The country settings

Each deployment serves one country: the UK, Ireland or Australia (`PLATFORM_REGION`). Its settings are open to anyone,
so the website and the parent app can show money, tax and year names the way that country does (added 6 Nov 2026,
go-live task 25).

**Endpoints:** `GET /api/settings` (no sign-in)

```mermaid
sequenceDiagram
  autonumber
  participant Y as application.yml and country.env
  participant API as API start-up (CountryConfig)
  participant C as Clock
  actor V as Anyone (website, parent app)
  participant W as Web app (useCountry)
  Y->>API: platform.region and platform.countries
  API->>API: pick the region's block and check it
  alt a value cannot work
    API-->>Y: refuse to start, naming the value
  else ok
    API->>C: the country's time zone
  end
  V->>W: open a page
  W->>API: GET /api/settings
  API-->>W: region, country, currency, locale, tax label and percent, time zone, school year, year names, legal entity
```

**Rules in the code**

- One region per deployment; schools are stored with it.
- The platform's clock follows the country's time zone (Australia: Queensland time, no daylight saving).
- Every value can be overridden by an environment variable, `PLATFORM_COUNTRIES_<REGION>_<NAME>`.
- Nothing in the settings is private, so the endpoint needs no sign-in.
