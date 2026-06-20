package db.storage;

public enum Type {
    INT {
        @Override
        public int getLen() {
            return 4;
        }
        @Override
        public Field parse(String s) {
            return new IntField(Integer.parseInt(s.trim()));
        }
    },
    STRING {
        @Override
        public int getLen() {
            return 128;
        }
        @Override
        public Field parse(String s) {
            return new StringField(s.trim(), getLen());
        }
    };

    public abstract int getLen();
    public abstract Field parse(String s);
}
