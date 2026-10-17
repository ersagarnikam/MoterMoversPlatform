package uk.co.motormovers.platform.content.model;

/** What a library resource is, so the web app knows how to show it. */
public enum ResourceKind {
    /** A recorded nursery rhyme, with its words. Linked to Red, Orange and Yellow sessions. */
    RHYME,
    /** A book to download, such as the Motor Movers rhyme book. */
    BOOK,
    /** A sheet to print: session cards, equipment lists, letters home. */
    PRINTABLE
}
