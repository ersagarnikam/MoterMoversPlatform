package uk.co.motormovers.platform.seed;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import uk.co.motormovers.platform.common.model.ColourStage;
import uk.co.motormovers.platform.content.model.ContentStatus;
import uk.co.motormovers.platform.content.model.GuidanceArticle;
import uk.co.motormovers.platform.content.model.HomeActivity;
import uk.co.motormovers.platform.content.model.LibraryResource;
import uk.co.motormovers.platform.content.model.ResourceKind;
import uk.co.motormovers.platform.content.repository.GuidanceArticleRepository;
import uk.co.motormovers.platform.content.repository.HomeActivityRepository;
import uk.co.motormovers.platform.content.repository.LibraryResourceRepository;
import uk.co.motormovers.platform.screening.model.MotorArea;
import uk.co.motormovers.platform.screening.model.ScreeningItem;
import uk.co.motormovers.platform.screening.repository.ScreeningItemRepository;
import uk.co.motormovers.platform.training.model.Course;
import uk.co.motormovers.platform.training.model.CourseTrack;
import uk.co.motormovers.platform.training.model.CourseQuiz;
import uk.co.motormovers.platform.training.model.InductionLesson;
import uk.co.motormovers.platform.training.model.QuizQuestion;
import uk.co.motormovers.platform.training.repository.CourseQuizRepository;
import uk.co.motormovers.platform.training.repository.CourseRepository;
import uk.co.motormovers.platform.training.repository.InductionLessonRepository;
import uk.co.motormovers.platform.training.repository.QuizQuestionRepository;

/**
 * The programme content a new deployment can start from: the assessment items, nursery rhymes and
 * printables, the one-hour induction, home activities and guidance for the parent app, and the two live
 * courses, with short quizzes between the practitioner course's sessions (for Pat to check and replace). The demo loads it approved; a real deployment loads it as drafts (StarterContentLoader), so
 * nothing reaches teachers or parents until the team has checked and approved it in Content approval.
 * Daily session plans are not included: the team writes them.
 */
@Component
public class StarterContent {

    /** What was loaded, for the demo to build on. */
    public record Loaded(Map<ColourStage, List<LibraryResource>> rhymes, List<InductionLesson> induction,
            Map<ColourStage, List<HomeActivity>> homeActivities, Course professional, Course parent) {
    }

    private final ScreeningItemRepository items;
    private final LibraryResourceRepository resources;
    private final InductionLessonRepository inductionLessons;
    private final HomeActivityRepository homeActivities;
    private final GuidanceArticleRepository guidance;
    private final CourseRepository courses;
    private final CourseQuizRepository quizzes;
    private final QuizQuestionRepository quizQuestions;

    public StarterContent(ScreeningItemRepository items, LibraryResourceRepository resources,
            InductionLessonRepository inductionLessons, HomeActivityRepository homeActivities,
            GuidanceArticleRepository guidance, CourseRepository courses, CourseQuizRepository quizzes,
            QuizQuestionRepository quizQuestions) {
        this.items = items;
        this.resources = resources;
        this.inductionLessons = inductionLessons;
        this.homeActivities = homeActivities;
        this.guidance = guidance;
        this.courses = courses;
        this.quizzes = quizzes;
        this.quizQuestions = quizQuestions;
    }

    /** Loads everything. With {@code approvedBy} null, reviewable content is left as drafts. */
    @Transactional
    public Loaded load(String approvedBy) {
        assessmentItems();
        Map<ColourStage, List<LibraryResource>> rhymes = resources(approvedBy);
        List<InductionLesson> induction = induction(approvedBy);
        Map<ColourStage, List<HomeActivity>> activities = homeActivities(approvedBy);
        guidance(approvedBy);
        Course professional = courses.save(new Course(CourseTrack.PROFESSIONAL, "Motor Movers practitioner course",
                "Four weekly live sessions of 90 minutes, recorded modules and short quizzes between sessions, "
                        + "and an assessment. Leads to the Motor Movers practitioner certificate, renewed yearly.",
                4, 90, 18_000, 6_000, true));
        quizzes(professional);
        Course parent = courses.save(new Course(CourseTrack.PARENT, "Motor Movers parent course",
                "Three weekly live sessions of 60 minutes with recorded home activities. Builds confidence to run "
                        + "the activities at home.",
                3, 60, 4_500, null, false));
        return new Loaded(rhymes, induction, activities, professional, parent);
    }

    /** Three short quizzes, after each of the first three live sessions; 80% passes. */
    private void quizzes(Course course) {
        quiz(course, 1, "The daily session", List.of(
                question("How long is the daily Motor Movers session?",
                        List.of("5 minutes", "10 minutes", "30 minutes", "An hour, once a week"), 1),
                question("Who takes part in the daily session?",
                        List.of("Only the children who find movement hardest", "The whole class together",
                                "Children whose parents ask for it"), 1),
                question("Why does the platform count sessions over the last 20 school days?",
                        List.of("To rank classes against each other", "Little and often is what makes the difference",
                                "To decide which children leave the programme"), 1)));
        quiz(course, 2, "Colour stages and assessment", List.of(
                question("How many colour stages does the programme have?", List.of("Three", "Five", "Seven"), 2),
                question("Which colour stage comes first?", List.of("Violet", "Red", "Green"), 1),
                question("What does a readiness profile show?",
                        List.of("A child's readiness for the movements of the programme, to plan their next steps",
                                "A medical label for the child", "A score to share with other parents"), 0),
                question("When you assess a child, you score...",
                        List.of("Each movement, one at a time", "The whole class at once", "Only the movements they "
                                + "get right"), 0)));
        quiz(course, 3, "Keeping children and families safe", List.of(
                question("How are children shown on the platform unless the school chooses otherwise?",
                        List.of("By full name", "By initials", "By photo"), 1),
                question("Who appears on camera in a live course session?",
                        List.of("Adults only", "Adults and children", "Anyone who joins"), 0),
                question("A parent asks about their own child's health in a live session. You...",
                        List.of("Discuss it with the group", "Signpost them to their GP or the child's school",
                                "Give them a label for it"), 1)));
    }

    private record Question(String text, List<String> options, int correct) {
    }

    private static Question question(String text, List<String> options, int correct) {
        return new Question(text, options, correct);
    }

    private void quiz(Course course, int afterSession, String title, List<Question> list) {
        CourseQuiz quiz = quizzes.save(new CourseQuiz(course, afterSession, title, 80));
        for (int i = 0; i < list.size(); i++) {
            Question q = list.get(i);
            quizQuestions.save(new QuizQuestion(quiz, i + 1, q.text(), q.options(), q.correct()));
        }
    }

    private void assessmentItems() {
        Object[][] defs = {
            {"BAL_SINGLE_LEG", MotorArea.BALANCE, "Single-leg balance, eyes open",
                "Watch for up to 10 seconds on each leg. Record the better attempt. Do not prompt.",
                new String[] {"Cannot hold for 2 seconds", "2 to 4 seconds, marked sway", "5 to 9 seconds, some sway", "10 seconds, steady"}},
            {"BAL_HEEL_TOE", MotorArea.BALANCE, "Heel-to-toe walk along a line",
                "Ten steps along a floor line, arms by the sides.",
                new String[] {"Steps off the line within 3 steps", "4 to 6 steps on the line", "7 to 9 steps on the line", "10 steps, steady"}},
            {"GM_HOP", MotorArea.GROSS_MOTOR, "Hopping on one foot",
                "Up to 5 hops on the preferred foot, staying in a hoop.",
                new String[] {"Cannot hop", "1 to 2 hops", "3 to 4 hops", "5 hops, controlled"}},
            {"GM_JUMP", MotorArea.GROSS_MOTOR, "Two-footed jump forward",
                "Jump from a standing start, landing on both feet together.",
                new String[] {"Does not leave the floor with both feet", "Jumps, unbalanced landing", "Jumps, lands with a step", "Jumps and lands together, steady"}},
            {"MD_THREAD", MotorArea.MANUAL_DEXTERITY, "Threading beads",
                "Thread as many large beads as possible in 30 seconds.",
                new String[] {"0 to 1 beads", "2 to 3 beads", "4 to 5 beads", "6 or more beads"}},
            {"MD_PEGS", MotorArea.MANUAL_DEXTERITY, "Posting pegs",
                "Post 6 pegs into a board with the preferred hand.",
                new String[] {"Fewer than 3 pegs", "3 to 4 pegs", "5 pegs", "All 6 pegs, smoothly"}},
            {"AC_CATCH", MotorArea.AIMING_AND_CATCHING, "Two-handed catch",
                "Five underarm throws of a beanbag from 2 metres.",
                new String[] {"No catches", "1 to 2 catches", "3 to 4 catches", "5 catches"}},
            {"AC_THROW", MotorArea.AIMING_AND_CATCHING, "Throwing at a target",
                "Five beanbag throws at a hoop on the floor 2 metres away.",
                new String[] {"None in the hoop", "1 to 2 in the hoop", "3 to 4 in the hoop", "5 in the hoop"}},
            {"PC_SIT", MotorArea.POSTURAL_CONTROL, "Sitting posture at a table",
                "Observe for one minute while the child draws.",
                new String[] {"Slumps or leans on the table throughout", "Frequently slumps", "Occasionally slumps", "Upright throughout"}},
            {"PC_SUPERMAN", MotorArea.POSTURAL_CONTROL, "Lying on the tummy, arms and legs raised",
                "Hold the position for up to 10 seconds.",
                new String[] {"Cannot lift arms and legs together", "Under 3 seconds", "3 to 9 seconds", "10 seconds"}},
            {"BC_CRAWL", MotorArea.BILATERAL_COORDINATION, "Cross-crawl",
                "Ten cross-crawl movements, opposite hand to opposite knee.",
                new String[] {"Same-side movements only", "Fewer than 5 correct", "5 to 9 correct", "10 correct, rhythmic"}},
            {"BC_CLAP", MotorArea.BILATERAL_COORDINATION, "Clap and jump pattern",
                "Copy a clap-jump-clap pattern three times.",
                new String[] {"Cannot copy the pattern", "Copies once", "Copies twice", "Copies three times"}},
        };
        int position = 1;
        for (Object[] d : defs) {
            String[] levels = (String[]) d[4];
            items.save(new ScreeningItem((String) d[0], position++, (MotorArea) d[1], (String) d[2], (String) d[3],
                    Map.of(0, levels[0], 1, levels[1], 2, levels[2], 3, levels[3])));
        }
    }

    /**
     * Traditional nursery rhymes for the Red, Orange and Yellow stages, the rhyme book and two printables.
     * There are no recordings yet, so every file URL is empty. Returns the rhymes by stage.
     */
    private Map<ColourStage, List<LibraryResource>> resources(String approvedBy) {
        Object[][] rhymeDefs = {
            {ColourStage.RED, "Row, row, row your boat", "Rocking forwards and backwards in pairs.",
                "Row, row, row your boat\nGently down the stream\nMerrily, merrily, merrily, merrily\n"
                        + "Life is but a dream"},
            {ColourStage.RED, "Humpty Dumpty", "Sitting tall, then a slow roll to the side.",
                "Humpty Dumpty sat on a wall\nHumpty Dumpty had a great fall\nAll the king's horses and all the "
                        + "king's men\nCouldn't put Humpty together again"},
            {ColourStage.ORANGE, "Incy Wincy Spider", "Finger climbing and big arm movements.",
                "Incy Wincy spider climbed up the water spout\nDown came the rain and washed the spider out\n"
                        + "Out came the sunshine and dried up all the rain\n"
                        + "So Incy Wincy spider climbed up the spout again"},
            {ColourStage.ORANGE, "The grand old Duke of York", "Marching, reaching up high and crouching low.",
                "Oh, the grand old Duke of York\nHe had ten thousand men\nHe marched them up to the top of the hill\n"
                        + "And he marched them down again\nAnd when they were up, they were up\n"
                        + "And when they were down, they were down\n"
                        + "And when they were only halfway up\nThey were neither up nor down"},
            {ColourStage.YELLOW, "Jack and Jill", "Climbing on the spot, then a forward roll on the mat.",
                "Jack and Jill went up the hill\nTo fetch a pail of water\nJack fell down and broke his crown\n"
                        + "And Jill came tumbling after"},
            {ColourStage.YELLOW, "Hickory dickory dock", "Swinging arms like a pendulum and crossing the middle.",
                "Hickory dickory dock\nThe mouse ran up the clock\nThe clock struck one\nThe mouse ran down\n"
                        + "Hickory dickory dock"},
        };
        Map<ColourStage, List<LibraryResource>> byStage = new EnumMap<>(ColourStage.class);
        for (Object[] d : rhymeDefs) {
            LibraryResource r = new LibraryResource(ResourceKind.RHYME, (String) d[1], (String) d[2],
                    (ColourStage) d[0], null, (String) d[3]);
            if (approvedBy != null) {
                r.review(ContentStatus.APPROVED, approvedBy);
            }
            byStage.computeIfAbsent((ColourStage) d[0], s -> new ArrayList<>()).add(resources.save(r));
        }

        Object[][] files = {
            {ResourceKind.BOOK, "Motor Movers rhyme book",
                "Every rhyme from the Red, Orange and Yellow stages, illustrated, with the actions for each line."},
            {ResourceKind.PRINTABLE, "Getting the space ready",
                "A one-page checklist: clear floor, shoes and socks off, jumpers and ties off, music ready."},
            {ResourceKind.PRINTABLE, "Letter home: starting Motor Movers",
                "A letter for families explaining the daily sessions and how to join in with the parent app."},
        };
        for (Object[] d : files) {
            LibraryResource r = new LibraryResource((ResourceKind) d[0], (String) d[1], (String) d[2], null, null,
                    null);
            if (approvedBy != null) {
                r.review(ContentStatus.APPROVED, approvedBy);
            }
            resources.save(r);
        }
        return byStage;
    }

    /** The one-hour induction: six lessons of ten minutes. */
    private List<InductionLesson> induction(String approvedBy) {
        Object[][] defs = {
            {"Why movement comes first", "The early movements that get a body ready for the classroom.",
                "Babies are meant to spend a long time on the floor: lifting the head, rolling, creeping on the "
                        + "tummy, crawling on hands and knees, then standing and walking. Each stage builds the "
                        + "balance and body control a child later needs to sit still, look at a page and hold a "
                        + "pencil.\n\n"
                        + "Car seats, buggies, baby seats and screens mean many children have had far less of this "
                        + "than they need. Motor Movers gives them a second chance at those movements, a little "
                        + "every day, as part of school life."},
            {"Early reflexes and learning", "Why some baby reflexes matter in Reception and Key Stage 1.",
                "Babies are born with reflexes that help them survive, such as the grasp reflex, where the hand "
                        + "closes when the palm is touched, and the reflex that straightens the arm on the side "
                        + "the head turns towards. Rolling and crawling help these reflexes fade during the first "
                        + "year or so, and more mature balance takes over.\n\n"
                        + "When an early reflex lingers, a child may find some things harder than they should be: "
                        + "a comfortable pencil grip, reading smoothly across the middle of the page, or writing "
                        + "up to a margin. The programme repeats the movements that help these reflexes fade."},
            {"The seven colour stages", "Matching the programme to where children are.",
                "The programme has seven stages following the colours of the rainbow, from Red to Violet. Each "
                        + "class works at one stage, chosen from assessment, so the movements match what the "
                        + "children are ready for rather than their age.\n\n"
                        + "Red, Orange and Yellow combine movement with nursery rhymes. You will find each rhyme's "
                        + "recording and words on the session screen and in Resources, along with the rhyme book."},
            {"Running the daily session", "Ten minutes, every school day, with the whole class.",
                "Clear a space, take off shoes, socks, jumpers and ties, and press Start session. The screen "
                        + "leads the class through a warm up, four activities and a cool down. The session is in "
                        + "addition to PE, not part of it.\n\n"
                        + "At the end, tap any child who was absent and press Complete session. The half-term "
                        + "counter on the Today screen shows how many school days the class has managed. Little "
                        + "and often is what makes the difference."},
            {"Assessment and the readiness profile", "What the scores tell you, and what they do not.",
                "Assess each child at the start and again later in the year, one movement at a time on a 0 to 3 "
                        + "scale. The readiness profile shows six areas as Emerging, Developing or Secure and "
                        + "suggests a colour stage.\n\n"
                        + "The profile describes movement readiness only. If a child is not moving on after a "
                        + "good run of daily sessions, share what you have seen with your SENCo, who can decide "
                        + "on next steps."},
            {"Keeping children and families safe", "Names, video, parents and the words we use.",
                "The platform shows initials and year group unless your school switches full names on, never "
                        + "records dates of birth, and keeps video off unless the school and each family agree.\n\n"
                        + "When you talk to families, describe what you see: \"finds balancing on one leg tricky\" "
                        + "rather than a label. If a parent wants the app, create a parent code from the child's "
                        + "readiness profile. Codes work once and expire after 14 days."},
        };
        List<InductionLesson> lessons = new ArrayList<>();
        int position = 1;
        for (Object[] d : defs) {
            InductionLesson l = new InductionLesson(position++, (String) d[0], (String) d[1], (String) d[2], 10, null);
            if (approvedBy != null) {
                l.review(ContentStatus.APPROVED, approvedBy);
            }
            lessons.add(inductionLessons.save(l));
        }
        return lessons;
    }

    private Map<ColourStage, List<HomeActivity>> homeActivities(String approvedBy) {
        Object[][] defs = {
            {"Starfish and seed", "Stretching out and curling up: body awareness", 5, new String[] {
                "Lie on your backs side by side, arms and legs out wide like a starfish",
                "Curl up small into a seed and hold for a count of five",
                "Stretch back out slowly. Repeat five times",
                "Finish lying still and breathing slowly together"}},
            {"Animal walk parade", "Moving on hands and feet for strength and coordination", 8, new String[] {
                "Bear walk across the room on hands and feet",
                "Crab walk back, tummy up",
                "Frog jumps to the door and back",
                "Choose a favourite animal and do it once more, slowly"}},
            {"Cushion path", "Balance on a wobbly path", 6, new String[] {
                "Lay a line of cushions or folded towels on the floor",
                "Walk along the path without stepping off",
                "Try it again, walking backwards with a grown-up's hand",
                "Finish by standing on one cushion on one leg, then the other"}},
            {"Sock ball catch", "Aiming and catching with a soft ball", 6, new String[] {
                "Roll a pair of socks into a ball",
                "Stand two big steps apart and throw underarm to each other",
                "Count how many catches you make in a row",
                "Try throwing into a basket or box from the same spot"}},
            {"Cross-crawl march", "Crossing the middle of the body", 5, new String[] {
                "March on the spot, lifting knees high",
                "Touch each knee with the opposite hand as it comes up",
                "Do ten slowly, then ten a bit faster",
                "Finish with ten slow ones to a favourite song"}},
            {"Rocking boat", "Rocking and rolling for balance", 5, new String[] {
                "Sit on the floor and hug your knees",
                "Rock gently backwards and forwards like a boat",
                "Rock side to side without letting go",
                "Lie down and roll slowly like a log to finish"}},
            {"Aeroplane", "Lying on the tummy, arms and legs lifted, for a strong back", 5, new String[] {
                "Lie on your tummies facing each other",
                "Lift arms and legs off the floor like an aeroplane",
                "Hold while you count to five, then rest",
                "Repeat three times and see if the count can grow"}},
        };
        Map<ColourStage, List<HomeActivity>> byStage = new EnumMap<>(ColourStage.class);
        for (ColourStage stage : ColourStage.values()) {
            List<HomeActivity> list = new ArrayList<>();
            for (int position = 1; position <= 5; position++) {
                Object[] d = defs[(stage.ordinal() + position - 1) % defs.length];
                HomeActivity a = new HomeActivity(stage, position, (String) d[0], (String) d[1], (Integer) d[2],
                        null, List.of((String[]) d[3]));
                if (approvedBy != null) {
                    a.review(ContentStatus.APPROVED, approvedBy);
                }
                list.add(homeActivities.save(a));
            }
            byStage.put(stage, list);
        }
        return byStage;
    }

    private void guidance(String approvedBy) {
        Object[][] articles = {
            {"Why movement comes before pencil skills",
                "Steady sitting and a strong core make writing and drawing easier.", null, 3,
                "Before a child can hold a pencil comfortably, they need a body that stays steady while their "
                        + "hand works. Crawling, rolling, climbing and balancing all build that steadiness.\n\n"
                        + "The Motor Movers sessions at school follow the same order: big movements first, small "
                        + "ones later. A few minutes of play at home each day helps the same skills grow.\n\n"
                        + "You do not need equipment. Cushions, socks, a hallway and some music are plenty."},
            {"Little and often",
                "Five minutes most days does more than an hour once a week.", null, 2,
                "Children build movement skills through repetition. A short, cheerful session most days "
                        + "works better than a long one now and then.\n\n"
                        + "Try linking the home activity to something that already happens every day, such as "
                        + "after tea or before the bath. The app counts the days for you.\n\n"
                        + "If a day is missed, simply pick it up again tomorrow."},
            {"Making space for movement at home",
                "Small homes work well. Here is how to set up safely.", null, 2,
                "Clear a strip of floor about two metres long. Move anything sharp or breakable, and use a rug "
                        + "or towel on hard floors.\n\n"
                        + "Bare feet are best for balance activities, as long as the floor is safe.\n\n"
                        + "Join in. Children copy what they see, and the activities are more fun together."},
            {"Getting the most from the parent course",
                "Three live sessions with the Motor Movers team.", null, 2,
                "The parent course runs as three live online sessions of an hour each. Only adults are on "
                        + "camera.\n\n"
                        + "The trainers show each activity and explain why it helps. Questions about one "
                        + "particular child are best taken to your child's school or your GP."},
            {"Rolling, rocking and crawling",
                "Why the early stages spend so long on the floor.", ColourStage.RED, 3,
                "The first stages of the programme spend a lot of time rolling, rocking and crawling. These "
                        + "movements help a child understand where their body is and how its two sides work "
                        + "together.\n\n"
                        + "At home, a rug and a bit of space are all you need. Make it a game: roll like a log, "
                        + "rock like a boat, crawl through a tunnel of chairs."},
            {"Balance games for the middle stages",
                "Simple ways to practise balance on the way to school.", ColourStage.YELLOW, 2,
                "Walking along a kerb line holding your hand, stepping only on the paving slabs, or standing "
                        + "like a flamingo while you wait for the bus all count as balance practice.\n\n"
                        + "Keep it light and stop while it is still fun."},
        };
        for (Object[] d : articles) {
            GuidanceArticle a = new GuidanceArticle((String) d[0], (String) d[1], (String) d[4], (ColourStage) d[2],
                    (Integer) d[3]);
            if (approvedBy != null) {
                a.review(ContentStatus.APPROVED, approvedBy);
            }
            guidance.save(a);
        }
    }
}
