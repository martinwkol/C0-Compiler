package edu.kit.kastel.vads.compiler.backend.register;

public record VirtualRegister(int id) implements Register {
    @Override
    public String toString() {
        return "%" + id();
    }

    @Override
    public boolean equals(Object object) {
        if (!(object instanceof VirtualRegister(int id1))) return false;
        return this.id == id1;
    }

    @Override
    public int hashCode() {
        return this.id ^ this.getClass().hashCode();
    }
    
}
