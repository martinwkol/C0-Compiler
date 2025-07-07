package edu.kit.kastel.vads.compiler.backend.register;

public enum PhysicalRegister implements Register {
    A("al", "ax", "eax", "rax"),
    B("bl", "bx", "ebx", "rbx"),
    C("cl", "cx", "ecx", "rcx"),
    D("dl", "dx", "edx", "rdx"),
    SI("sil", "si", "esi", "rsi"),
    DI("dil", "di", "edi", "rdi"),
    BP("bpl", "bp", "ebp", "rbp"),
    SP("bsl", "sp", "esp", "rsp"),
    R8("r8b", "r8w", "r8d", "r8"),
    R9("r9b", "r9w", "r9d", "r9"),
    R10("r10b", "r10w", "r10d", "r10"),
    R11("r11b", "r11w", "r11d", "r11"),
    R12("r12b", "r12w", "r12d", "r12"),
    R13("r13b", "r13w", "r13d", "r13"),
    R14("r14b", "r14w", "r14d", "r14"),
    R15("r15b", "r15w", "r15d", "r15"),
    R16("r16b", "r16w", "r16d", "r16");


    public static final PhysicalRegister Return = A;

    public static final PhysicalRegister DividendMS = D;
    public static final PhysicalRegister DividendLS = A;
    public static final PhysicalRegister Quotient = A;
    public static final PhysicalRegister Remainder = D;

    public static final PhysicalRegister ShiftRegister = C;

    public static final PhysicalRegister[] All = {
            A, B, C, D, SI, DI, BP, SP, R8, R9, R10, R11, R12, R13, R14, R15, R16
    };
    public static final PhysicalRegister[] FreelyUsable = {
            A, B, C, D, SI, DI, R8, R9, R10
    };
    public static final PhysicalRegister Temp = R11;

    public final String name1byte;
    public final String name2bytes;
    public final String name4bytes;
    public final String name8bytes;

    PhysicalRegister(String name1byte, String name2bytes, String name4bytes, String name8bytes) {
        this.name1byte = name1byte;
        this.name2bytes = name2bytes;
        this.name4bytes = name4bytes;
        this.name8bytes = name8bytes;
    }

}
