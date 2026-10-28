// The words of the user guide (docs/user-guide): how each kind of user does their everyday tasks, step by step.
// Written for the people who use the platform, in the words on the screen. Each guide: what you can do, then
// "How do I…?" tasks (numbered steps, a screenshot, a tip), then questions and answers.

/** For everyone, at the start of every guide. */
export const GETTING_STARTED = {
  title: 'Getting started',
  tasks: [
    {
      title: 'Sign in',
      steps: [
        'Go to the Motor Movers website and choose Sign in.',
        'Choose your role, then enter your email address and password.',
        'Choose Sign in. You go straight to your own home page.',
      ],
      tip: 'Staff and trainers are invited by email: the first time, use the link in the email to choose a password. Passwords need at least 10 characters.',
      shots: ['site-sign-in'],
      captions: ['Choosing your role on the website.'],
    },
    {
      title: 'Reset a forgotten password',
      steps: [
        'On the sign-in page, choose Forgot your password?',
        'Enter your email address and choose Email me a link.',
        'Open the email, follow the link and choose a new password. The link works once.',
      ],
    },
    {
      title: 'Change how the platform looks, and sign out',
      steps: [
        'Use Light, Dark or System at the top of any page to change the colours. System follows your computer.',
        'Choose Sign out at the top right when you have finished, especially on a shared computer.',
      ],
    },
  ],
}

export const GUIDES = [
  {
    slug: 'class-teacher',
    title: 'Class teacher',
    strap: 'Running the daily session, assessing children, sharing progress with parents and booking training.',
    can: [
      'Run today’s 10-minute session and record who took part.',
      'Assess children on the programme’s exercises (1 to 4) and on the developmental charts.',
      'See each child’s assessment profile.',
      'Give parents a code for the parent app.',
      'Use the resource library, and ask for a place on the practitioner course.',
    ],
    tasks: [
      {
        title: 'Complete the induction (new staff)',
        steps: [
          'Sign in. If sessions are locked, choose Go to the induction.',
          'Open each of the six parts in turn, read or watch it, and choose Mark as done.',
          'When every part is done, your class’s sessions unlock.',
        ],
        tip: 'The induction takes about an hour in all. You can stop and come back: finished parts stay ticked.',
        shots: ['newteacher-induction'],
        captions: ['The induction, one part at a time.'],
      },
      {
        title: 'Run today’s session',
        steps: [
          'Sign in. Your class page shows today’s session, chosen for your class’s stage, module and day.',
          'Choose Start session. Follow the plan and the timer with the class: a warm-up, four activities and a cool-down.',
          'At the end, tap any child who was absent today.',
          'Choose Complete session. The day turns green on the 20-day counter.',
        ],
        tip: 'Sessions can only be recorded on school days. If you miss a day, simply carry on the next day: the counter shows how regular the class has been over the last 20 school days.',
        shots: ['teacher-player'],
        captions: ['The session player, with the plan and the rhyme’s words.'],
      },
      {
        title: 'Score the class on an exercise',
        steps: [
          'From your class, choose Assess children. Exercises (1 to 4) is open.',
          'Choose the Exercise, and When: Start (the start of the colour), the end of a module, or the end of the colour. The point to do next is marked (suggested).',
          'Open The movement to read what each score looks like: 1 Not yet, 2 Developing, 3 Nearly there, 4 Confident and accurate.',
          'Watch the class do the exercise and tap 1, 2, 3 or 4 beside each child. Each score saves as you tap.',
        ],
        tip: 'Scores are kept on your device if the internet drops and sent when it is back. Tapping another score at the same point replaces the earlier one.',
        shots: ['teacher-assess'],
        captions: ['Scoring the class on one exercise, with the descriptions of each score.'],
      },
      {
        title: 'Record movements on the developmental charts',
        steps: [
          'In Assess children, choose Developmental charts, then Open charts beside a child.',
          'Enter the child’s Age in months, and When: the start, middle or end of the school year.',
          'Tap each movement the child does confidently and securely. It shows Secure at that age.',
          'Write a comment for each chart if you wish, and choose Save comment.',
        ],
        tip: 'Do this at the start, middle and end of the school year. No date of birth is needed. A movement tapped by mistake can be tapped again to take it off.',
        shots: ['teacher-development'],
        captions: ['The fine and gross motor charts for one child.'],
      },
      {
        title: 'Read a child’s assessment profile',
        steps: [
          'In your class list, choose a child.',
          'The profile starts with the two assessments: movements secure on each chart, the table of exercise scores at each point, start and end totals, and the summary comments.',
          'If a pattern of low scores shows, the profile says May benefit from extra support, with the reasons.',
          'The earlier readiness check (0 to 3) follows underneath.',
        ],
        tip: 'The profile describes progress with the programme. It is never a diagnosis, and every view of a child’s record is logged.',
        shots: ['teacher-assessment-profile'],
        captions: ['A child’s assessment profile.'],
      },
      {
        title: 'Use the readiness check (0 to 3)',
        steps: [
          'In Assess children, choose Readiness check (0 to 3), then choose a child.',
          'For each movement, ask the child to do it and choose the description that matches what you saw.',
          'Choose Next item after each one, and Finish child at the end.',
        ],
        tip: 'This earlier check stays alongside the new assessments until Pat confirms it can go.',
        shots: ['teacher-screening-item'],
        captions: ['One movement: the instruction and four descriptions to choose from.'],
      },
      {
        title: 'Give a parent a code for the parent app',
        steps: [
          'Open the child’s readiness profile.',
          'Under Parent app, choose Create a parent code.',
          'Give the code to the child’s parent or carer. It works once, and shows when it expires.',
        ],
        tip: 'Parent links only work if your school has switched them on. The head can remove a parent’s access at any time.',
      },
      {
        title: 'Ask for a place on a course',
        steps: [
          'Choose Training.',
          'Find the course and cohort you would like, and choose Ask for a place.',
          'Your head teacher is emailed to approve it. The request shows under Your requests until they decide.',
          'Once approved, the place appears under Your courses, with the live sessions to join.',
        ],
        tip: 'You can withdraw a request while it is waiting. Once booked, you can cancel the place until the first session starts.',
        shots: ['staff-requested'],
        captions: ['A request waiting for the head’s approval.'],
      },
      {
        title: 'Find rhymes and printables',
        steps: ['Choose Resources.', 'Open the rhyme book, a rhyme with its words, or a printable sheet for your stage.'],
      },
    ],
    faq: [
      {
        q: 'My sessions are locked. Why?',
        a: 'New staff complete the one-hour induction first. Choose Go to the induction; sessions unlock when every part is done.',
      },
      { q: 'I cannot see a child’s full name.', a: 'Your school shows children by initials unless the head switches full names on in School setup.' },
      { q: 'A child joined or left my class.', a: 'Your head teacher adds, moves and records leavers in School setup.' },
    ],
  },
  {
    slug: 'head-teacher',
    title: 'Head teacher and SENCo',
    strap: 'Following progress, setting the school up, approving training and paying invoices.',
    can: [
      'See progress and daily participation across the school, and download summaries.',
      'Invite staff, set up classes and children, and choose privacy settings.',
      'Approve staff course places, and see and pay the school’s invoices.',
    ],
    tasks: [
      {
        title: 'See how the school is doing',
        steps: [
          'Sign in. Your progress dashboard shows children assessed, children who have moved up a stage, daily participation and active classes.',
          'Look at participation class by class: a class below 70% over the last 20 school days is highlighted.',
          'Under Exports, download the governor summary or the anonymised research file.',
        ],
        shots: ['head-dashboard'],
        captions: ['The progress dashboard.'],
      },
      {
        title: 'Follow the assessments',
        steps: [
          'On Progress, scroll to Assessments for this school year.',
          'For each class (with its colour), see how many children have been assessed at each point, the average movements secured this year, and how many children are at each exercise level.',
          'Underneath, the children who may benefit from extra support are listed with the first reason. Choose a child to open their profile.',
        ],
        tip: 'Extra support describes a pattern of low scores, never a cause. Talk it through with the class teacher.',
        shots: ['head-assessments'],
        captions: ['Assessments across the school.'],
      },
      {
        title: 'Invite a member of staff',
        steps: [
          'Choose School setup.',
          'Under Staff, enter their name and email, choose Teacher or Head / SENCo.',
          'Choose Add and email a sign-in link. They receive an email to choose a password.',
        ],
        tip: 'When someone leaves, choose Switch off beside their name. Their account stops working within a minute.',
        shots: ['head-setup'],
        captions: ['School setup: staff, classes and privacy.'],
      },
      {
        title: 'Set up a class and its children',
        steps: [
          'In School setup, enter the class name, year group, stage and teacher, and choose Add class.',
          'Choose Edit class and children, type the children one per line, and choose Add children.',
          'Use Move to… to move a child to another class, or Left the school when a child leaves.',
        ],
        tip: 'Records of children who have left are deleted automatically a year after their leaving date.',
        shots: ['head-setup-class'],
        captions: ['A class and its children.'],
      },
      {
        title: 'Choose the school’s privacy settings',
        steps: [
          'In School setup, under Privacy, tick or untick:',
          'Show children’s full names to staff (otherwise initials and year group only).',
          'Allow video of children, for children whose families have agreed.',
          'Let the trust or group lead see individual children (otherwise summaries only).',
        ],
      },
      {
        title: 'Approve or decline a staff course place',
        steps: [
          'When a member of staff asks for a place, you get an email. Choose Payments.',
          'Under Course places to approve, check the course, cohort and price.',
          'Choose Approve to book the place and add its invoice to the school’s account, or Decline.',
        ],
        tip: 'The member of staff is emailed either way. Places you book for yourself are booked straight away and invoiced to the school.',
        shots: ['head-requests'],
        captions: ['A request waiting for approval.'],
      },
      {
        title: 'Pay an invoice by card',
        steps: [
          'Choose Payments. Invoices that are due show Pay by card.',
          'Choose Pay by card and pay on the payment provider’s secure page.',
          'You come back to the Payments page. The invoice shows as paid, by card, in a moment.',
        ],
        tip: 'Your card details are entered on the payment provider’s page, never on Motor Movers. You can still pay by bank transfer, quoting the invoice reference.',
        shots: ['head-payments'],
        captions: ['The school’s invoices, with Pay by card.'],
      },
      {
        title: 'Remove a parent’s access to a child',
        steps: ['On the progress dashboard, find the child under Parent app links.', 'Choose Remove and confirm.'],
      },
    ],
    faq: [
      {
        q: 'A member of staff did not get their invitation email.',
        a: 'Check the email address in School setup and ask them to look in their junk folder. They can also use Forgot your password? on the sign-in page.',
      },
      {
        q: 'Who else can see our children?',
        a: 'Only staff at your school, and parents you have linked through a code. The trust or group lead sees summaries only unless you tick the setting that allows more.',
      },
    ],
  },
  {
    slug: 'trust-lead',
    title: 'Trust or group lead',
    strap: 'Licences, results and spending across the schools in your trust or group.',
    can: [
      'See every school’s licence, renewal date and stages.',
      'Compare the share of children assessed and daily participation.',
      'See what each school has paid and owes.',
    ],
    tasks: [
      {
        title: 'Compare your schools',
        steps: [
          'Sign in. Your page lists every school in the trust or group.',
          'For each school, see its licence status (active, renewal due, free trial), renewal date, stages, children assessed and participation.',
          'Under Spending, see what each school has paid and what is outstanding, with a total.',
          'For each school, see the share of children developmentally assessed this school year and how many may benefit from extra support.',
        ],
        tip: 'You see totals and percentages. Individual children are only visible if a school chooses to allow it.',
        shots: ['trust-dashboard'],
        captions: ['The trust or group page.'],
      },
    ],
    faq: [{ q: 'A school’s renewal is due. What do I do?', a: 'Contact the Motor Movers team, who renew licences and send the invoice.' }],
  },
  {
    slug: 'practitioner',
    title: 'Practitioner',
    strap: 'Booking the practitioner course, joining live sessions, taking the quizzes and getting your certificate.',
    can: [
      'Book a place on a cohort, and cancel it before it starts.',
      'Join the live sessions from the platform, without a Zoom account.',
      'Take the quizzes between sessions, and download your certificate.',
      'See and pay what is due.',
    ],
    tasks: [
      {
        title: 'Book a place',
        steps: [
          'Sign in and choose My course (the Training page).',
          'Find the practitioner course and a cohort that suits you: each shows its start date, day and time, trainer and places left.',
          'Choose Book a place. It appears under Your courses.',
        ],
        tip: 'Staff at a Motor Movers school ask their head for a place instead, as the school pays.',
        shots: ['practitioner-training'],
        captions: ['The Training page and the practitioner course.'],
      },
      {
        title: 'Join a live session',
        steps: [
          'You get an email reminder the day before. Sessions are listed under Your live sessions, in your own time zone.',
          'From 15 minutes before the start, choose Join live session.',
          'The session opens in the platform. Allow your camera and microphone if asked.',
        ],
        tip: 'Adults only on camera, and no discussion of an individual child’s condition: questions about a particular child go to the family’s GP or school.',
      },
      {
        title: 'Take a quiz',
        steps: [
          'Under Your courses, each quiz opens after its live session. Choose Take quiz.',
          'Choose one answer for every question, then Send answers.',
          'You see your score. If you have not passed yet, it says which questions to look at again: choose Try again.',
        ],
        tip: 'You can take a quiz as often as you like. Your best score counts.',
        shots: ['practitioner-quiz', 'practitioner-quiz-result'],
        captions: ['A quiz.', 'The result, with the question to look at again.'],
      },
      {
        title: 'Get your certificate',
        steps: [
          'When you have attended every session, passed every quiz and your trainer has recorded the assessment, your certificate is issued.',
          'It is emailed to you as a PDF. On the Training page, choose View your certificate.',
          'Choose Download PDF to save it, or Print.',
        ],
        tip: 'The certificate lasts a year. You are reminded a month before it expires.',
        shots: ['practitioner-certificate'],
        captions: ['Your certificate.'],
      },
      {
        title: 'Cancel a place',
        steps: ['Under Your courses, choose Cancel place and confirm.'],
        tip: 'You can cancel until the first live session starts. After that, contact the Motor Movers team.',
      },
      {
        title: 'Pay what is due',
        steps: [
          'Choose Payments. Anything due is listed with its reference.',
          'Choose Pay by card and pay on the payment provider’s secure page, or pay as shown on your invoice.',
        ],
      },
    ],
    faq: [
      { q: 'The Join button is not there.', a: 'It appears 15 minutes before the session starts. Refresh the page if you opened it earlier.' },
      { q: 'My quiz says it opens after a session.', a: 'Each quiz opens once its live session has started, or once your trainer has marked you present.' },
      { q: 'Nobody can see or hear me in the session.', a: 'Allow the camera and microphone when your browser asks, or in your browser’s settings for the Motor Movers site.' },
    ],
  },
  {
    slug: 'parent-or-carer',
    title: 'Parent or carer',
    strap: 'Using the parent app at home, Motor Movers at Home if your child’s school is not on Motor Movers, the parent course and paying online.',
    can: [
      'Link your child with a code from their school, and follow how they are getting on.',
      'Do a short activity or session at home most days.',
      'No school on Motor Movers? Use Motor Movers at Home, in the app or on the website.',
      'Book and join the live parent course, and pay by card.',
    ],
    tasks: [
      {
        title: 'Link your child in the app',
        steps: [
          'Ask your child’s teacher for a parent code.',
          'Sign in to the parent app, or create an account.',
          'Choose I have a code, enter the parent code and choose Link my child.',
        ],
        tip: 'Each code works once. The school can remove your access at any time.',
        shots: ['app-sign-in', 'app-progress'],
        captions: ['Signing in.', 'Your child, linked.'],
        phone: true,
      },
      {
        title: 'Do today’s activity',
        steps: [
          'Open the app on Today. It suggests a short activity for your child’s stage.',
          'Open it and follow the steps together, with the timer if you like.',
          'Tap We did it. The 14-day tracker fills in.',
        ],
        tip: 'Little and often is what makes the difference: five minutes most days.',
        shots: ['app-today', 'app-guidance'],
        captions: ['Today’s activity.', 'Guidance articles.'],
        phone: true,
      },
      {
        title: 'See how your child is getting on at school',
        steps: [
          'Choose Progress, then your child.',
          'Movements shows how many movements on the programme’s two charts (hands and fingers, whole body) the school has seen your child do securely.',
          'Exercises shows each exercise of your child’s colour, from the first time to the latest: Not yet, Getting there, Nearly or Got it.',
        ],
        tip: 'These are not scores or comparisons with other children, and never a diagnosis. Your child’s class teacher is the best person to talk to.',
        shots: ['app-child', 'app-child-assessments'],
        captions: ['Your child’s page.', 'Exercises, in plain words.'],
        phone: true,
      },
      {
        title: 'Start Motor Movers at Home (no school needed)',
        steps: [
          'Create a parent account in the app, or on the website: choose For families, then Create a parent account.',
          'Add your child: a first name or nickname and their age in years and months. In the app choose Add your child on Today; on the website, choose At home.',
          'Do the short check: for each movement, choose Yes, Not yet or Not sure. Then choose Start with the suggested colour, or choose another.',
        ],
        tip: 'Only you, and anyone you invite, see your child. We never ask for a date of birth. The check is suggested again every three months.',
        shots: ['app-home-check', 'home-web-check'],
        captions: ['The check in the app.', 'The same check on the website.'],
        mixed: true,
      },
      {
        title: 'Do the daily home session',
        steps: [
          'In the app, open Today. On the website, choose At home, then your child.',
          'Choose Start and do the four exercises together, one at a time. Choose Next exercise after each.',
          'Choose We did it. The first time your child does an exercise, choose how it went: Not yet, Getting there, Nearly or Got it.',
          'At the end of each module (ten sessions), a reminder asks you to rate its exercises again, so you can see the progress.',
        ],
        tip: 'Little and often: about ten minutes most days. Choosing a rating shows what it looks like, so you can check.',
        shots: ['app-home-today', 'home-web-child'],
        captions: ['Today in the app.', 'Your child’s page on the website.'],
        mixed: true,
      },
      {
        title: 'Share with another parent or carer',
        steps: [
          'In the app choose Account, then Family and sharing. On the website choose At home, then Family and sharing.',
          'Type their email address and choose Email a code, or choose Get a code and pass it on yourself.',
          'They sign in (or create an account) and enter the code under Join a family. It works once, for 14 days.',
        ],
        shots: ['app-family', 'home-web-family'],
        captions: ['Your family in the app.', 'Family and sharing on the website.'],
        mixed: true,
      },
      {
        title: 'Link the school if it joins later',
        steps: [
          'Ask the school for a parent code.',
          'Open your child’s page. In the app choose [name]’s school has joined Motor Movers; on the website it is under Settings.',
          'Enter the code, agree to share what you recorded at home, and choose Link the school.',
        ],
        tip: 'The class teacher then sees what you recorded at home, and does the assessing from then on.',
      },
      {
        title: 'Book and join the parent course',
        steps: [
          'Choose Course, find a group that suits you and choose Book a place.',
          'Your live sessions are listed at the top. From 15 minutes before the start, choose Join in Zoom.',
          'The meeting opens in the Zoom app (or Zoom’s page offers it).',
        ],
        tip: 'Allow notifications when the app asks: it tells you when a session is about to start, and when a payment is due.',
        shots: ['app-course'],
        captions: ['Live sessions in the app.'],
        phone: true,
      },
      {
        title: 'Pay by card',
        steps: [
          'Choose Account, then Payment details and history (or tap the reminder on Today).',
          'Choose Pay by card and pay on the payment provider’s secure page.',
          'Come back to the app and pull down to refresh: the payment shows as paid.',
        ],
        shots: ['app-payments'],
        captions: ['Payments, with Pay by card.'],
        phone: true,
      },
    ],
    faq: [
      { q: 'My code does not work.', a: 'Each code works once and expires. Ask the teacher for a new one.' },
      { q: 'I am not getting notifications.', a: 'Allow notifications for Motor Movers in your phone’s settings, then sign out and in again.' },
      { q: 'Can I use a computer instead?', a: 'Yes: sign in on the Motor Movers website to book the course, join sessions and pay. Motor Movers at Home works there too, under At home.' },
      { q: 'Does the school see what we do at home?', a: 'Not with Motor Movers at Home, unless you link the school. The home activities you record for a school child are yours too.' },
    ],
  },
  {
    slug: 'trainer',
    title: 'Trainer',
    strap: 'Running your live sessions, keeping the register and completing your cohorts.',
    can: [
      'Start the live sessions of the cohorts given to you.',
      'Keep the register, seeing who has joined as it happens.',
      'Record each learner’s assessment, which completes their certificate.',
    ],
    tasks: [
      {
        title: 'Start a live session',
        steps: [
          'Sign in. Live sessions lists the sessions of your cohorts.',
          'Choose Start session. The meeting opens in the platform, already signed in as the host.',
          'Learners can join from 15 minutes before the start.',
        ],
        tip: 'You can open a session early to check everything works. You get a reminder email the day before each session.',
        shots: ['trainer-live'],
        captions: ['Your live sessions.'],
      },
      {
        title: 'Keep the register',
        steps: [
          'Beside the meeting, the register shows who is booked, who has joined and when. It refreshes every 15 seconds.',
          'Tick each learner who attended, or choose Tick everyone who joined.',
          'Untick anyone ticked by mistake.',
        ],
        tip: 'Joined means they opened the session. Check they stayed before ticking: ticks count towards their certificate.',
        shots: ['live-register'],
        captions: ['The register.'],
      },
      {
        title: 'Record the assessment and complete the cohort',
        steps: [
          'Choose My cohorts.',
          'For each learner, check their sessions and quizzes, and tick Passed when they pass the assessment.',
          'When everything is complete, the certificate is issued and emailed to them automatically.',
        ],
        tip: 'Once a certificate is issued it cannot be taken back here. Contact the team if something is wrong.',
        shots: ['trainer-cohorts'],
        captions: ['My cohorts.'],
      },
    ],
    faq: [
      { q: 'I cannot see a session.', a: 'You see only the cohorts the team has given you. Ask the team to give you the cohort.' },
      {
        q: 'Zoom says another meeting is in progress.',
        a: 'A Zoom login runs one meeting at a time. End the other meeting, or ask the team to give you your own Zoom login.',
      },
    ],
  },
  {
    slug: 'motor-movers-team',
    title: 'Motor Movers team',
    strap: 'Publishing content, signing schools up, running courses, quizzes and trainers, and recording payments.',
    can: [
      'Write and approve every piece of content.',
      'Sign schools up, set licences, plans and prices, and add trusts or groups.',
      'Run courses, cohorts, quizzes, live sessions and the training team.',
      'Record payments and see income.',
    ],
    tasks: [
      {
        title: 'See what needs attention today',
        steps: [
          'Sign in. Home lists what needs doing first: live sessions today, drafts waiting for sign-off, overdue invoices, licences to renew, Zoom clashes and certificates running out.',
          'Each card links to where it is done. Cards with nothing to do say All clear.',
        ],
        shots: ['admin-home'],
        captions: ['Home: what needs attention today.'],
      },
      {
        title: 'Add and approve content',
        steps: [
          'Choose Content approval and the kind of content (class sessions, home activities, parent guidance, resources or staff induction).',
          'Choose Add…, fill in the form and save. It waits as a draft.',
          'Check the draft and choose Approve. It then reaches teachers or parents.',
        ],
        tip: 'Wording that reads like a diagnosis is highlighted and cannot be approved until it is reworded.',
        shots: ['admin-content-wording'],
        captions: ['The wording check blocking a draft.'],
      },
      {
        title: 'Sign a school up',
        steps: [
          'Choose Schools & licences. Under Sign a school up, enter the school name, its trust or group, plan, start and end dates, and tick its stages.',
          'Choose Sign up school.',
          'Choose Set up beside the school and, under Staff, add the head teacher as Head / SENCo. They are emailed a link to set up their account.',
        ],
        shots: ['admin-schools'],
        captions: ['Schools & licences.'],
      },
      {
        title: 'Change plan prices',
        steps: ['Choose Plans and prices.', 'Set a new price with the date it applies from. Prices already in force are never changed.'],
      },
      {
        title: 'Open a cohort and give it a trainer',
        steps: [
          'Choose Courses and open the course.',
          'Choose Open a cohort.',
          'Enter the name, start date, start time (UK), day and time, places and recording, and choose the trainer.',
          'Choose Open cohort. Its weekly sessions are dated, and it is open for booking.',
        ],
        tip: 'If a cohort’s sessions overlap another cohort’s on the same Zoom login, a Zoom clash warning shows: give it a trainer with their own Zoom login, or move it.',
      },
      {
        title: 'Write a quiz',
        steps: [
          'Open the course from Courses, choose Quizzes, then Add a quiz.',
          'Enter the title, the live session it follows and the pass mark.',
          'For each question, type the answers one per line and choose the right one. Choose Save quiz.',
        ],
        tip: 'Learners need to pass every quiz for the certificate. A quiz learners have taken can be changed but not deleted.',
        shots: ['admin-quiz-editor'],
        captions: ['Writing a quiz.'],
      },
      {
        title: 'Follow bookings, attendance and certificates',
        steps: [
          'Open the course from Courses. Under a cohort, choose Bookings and attendance.',
          'Tick the sessions each learner attended and the assessment. The certificate issues itself when everything is complete.',
          'Choose Cancel place to cancel a booking; a school invoice still due is cancelled with it.',
        ],
        shots: ['admin-bookings'],
        captions: ['A cohort’s bookings.'],
      },
      {
        title: 'Add a trainer',
        steps: [
          'Choose Trainers, then Add a trainer.',
          'Enter their name, email and, if they have one, their own Zoom login. Choose Add trainer.',
          'They are emailed a link to choose a password. Give them cohorts when you open or edit one.',
        ],
        tip: 'Choose Switch off when a trainer leaves, and give their cohorts to someone else.',
        shots: ['admin-trainers'],
        captions: ['The training team.'],
      },
      {
        title: 'Record an invoice or a payment',
        steps: [
          'Choose Payments, then Record a payment.',
          'Choose the payer (a school or a person), what it is for, the amount and the invoice date, then Record payment.',
          'When the money arrives by transfer, choose Mark paid today. Card payments mark themselves paid.',
        ],
        shots: ['admin-payments'],
        captions: ['Payments and income.'],
      },
    ],
    faq: [
      { q: 'A school cannot see a stage.', a: 'Check the stages on its licence in Schools & licences.' },
      { q: 'A parent paid by card but it still shows as due.', a: 'Card payments are marked paid when the payment provider confirms them, usually within seconds. If it stays due, check the payment in the provider’s dashboard.' },
    ],
  },
]
