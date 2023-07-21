import java.util.PriorityQueue;

/**
 * Although this class has a history of several years,
 * it is starting from a blank-slate, new and clean implementation
 * as of Fall 2018.
 * <P>
 * Changes include relying solely on a tree for header information
 * and including debug and bits read/written information
 * 
 * @author Ow	en Astrachan
 *
 * Revise
 */

public class HuffProcessor {

	private class HuffNode implements Comparable<HuffNode> {
		HuffNode left;
		HuffNode right;
		int value;
		int weight;

		public HuffNode(int val, int count) {
			value = val;
			weight = count;
		}
		public HuffNode(int val, int count, HuffNode ltree, HuffNode rtree) {
			value = val;
			weight = count;
			left = ltree;
			right = rtree;
		}

		public int compareTo(HuffNode o) {
			return weight - o.weight;
		}
	}

	public static final int BITS_PER_WORD = 8;
	public static final int BITS_PER_INT = 32;
	public static final int ALPH_SIZE = (1 << BITS_PER_WORD); 
	public static final int PSEUDO_EOF = ALPH_SIZE;
	public static final int HUFF_NUMBER = 0xface8200;
	public static final int HUFF_TREE  = HUFF_NUMBER | 1;

	private boolean myDebugging = false;
	
	public HuffProcessor() {
		this(false);
	}
	
	public HuffProcessor(boolean debug) {
		myDebugging = debug;
	}
	private int[] getCounts(BitInputStream in) {
		int[] count = new int[ALPH_SIZE + 1];
		for (int val = in.readBits(BITS_PER_WORD); val != -1; val = in.readBits(BITS_PER_WORD)) {
			count[val]++;
		}
		return count;
	}
	

	private void makeEncodings (HuffNode root, String s, String[] encodings) {
		if (root.left == null && root.right == null) {
			encodings[root.value] = s;
			return;
		}
		else {
			makeEncodings(root.left, s + "0", encodings);
			makeEncodings(root.right, s + "1", encodings);	
		}
	}

	private HuffNode makeTree(int[] counts) {
		PriorityQueue<HuffNode> pq = new PriorityQueue<>();
		int j = 0;
		while (j < counts.length) {
			if (counts[j] > 0) {
				pq.add(new HuffNode(j, counts[j], null, null));
			}
			j++;
		}
		pq.add(new HuffNode(PSEUDO_EOF, 1, null, null));
	
		while (pq.size() > 1) {
			HuffNode left = pq.remove();
			HuffNode right = pq.remove();
			HuffNode t = new HuffNode(0, right.weight + left.weight, left, right);
			pq.add(t);
		}
		HuffNode root = pq.remove();
		return root;
	}
	


	private void writeTree (HuffNode h, BitOutputStream out) {
		if (h.right != null && h.left!= null) {
			out.writeBits(1, 0);
			writeTree(h.left, out);
			writeTree(h.right, out);
		}
		else {
			out.writeBits(1, 1);
			out.writeBits(BITS_PER_WORD+1, h.value);
		}
	}

	private HuffNode readTree(BitInputStream in) {
		int bit = in.readBits(1);
		if (bit == -1) {
			throw new HuffException("Invalid bit");
		}
		if (bit == 0) {
			HuffNode leftChild = readTree(in);
			HuffNode rightChild = readTree(in);
			return new HuffNode(0, 0, leftChild, rightChild);
		}
		else {
			int nodeValue = in.readBits(BITS_PER_WORD + 1);
			return new HuffNode(nodeValue, 0, null, null);
		}
	}
	
	/**
	 * Compresses a file. Process must be reversible and loss-less.
	 *
	 * @param in
	 *            Buffered bit stream of the file to be compressed.
	 * @param out
	 *            Buffered bit stream writing to the output file.
	 */
	
	 public void compress(BitInputStream in, BitOutputStream out) {
		int[] counts = getCounts(in);
		HuffNode root = makeTree(counts);
		in.reset();
		out.writeBits(BITS_PER_INT, HUFF_TREE);
		writeTree(root, out);
		String[] encodings = new String[ALPH_SIZE + 1];
		makeEncodings(root, "", encodings);
	
		int bits;
		while ((bits = in.readBits(BITS_PER_WORD)) != -1) {
			String encoding = encodings[bits];
			out.writeBits(encoding.length(), Integer.parseInt(encoding, 2));
		}
		String eofEncoding = encodings[PSEUDO_EOF];
		out.writeBits(eofEncoding.length(), Integer.parseInt(eofEncoding, 2));
		out.close();
	}
	

	/**
	 * Decompresses a file. Output file must be identical bit-by-bit to the
	 * original.
	 *
	 * @param in
	 *            Buffered bit stream of the file to be decompressed.
	 * @param out
	 *            Buffered bit stream writing to the output file.
	 */
	public void decompress(BitInputStream in, BitOutputStream out) {
		int bits = in.readBits(BITS_PER_INT);
		if (bits != HUFF_TREE) {
			throw new HuffException("Invalid magic number " + bits);
		}
	
		HuffNode root = readTree(in);
		HuffNode current = root;
		while (true) {
			bits = in.readBits(1);
			if (bits == -1) {
				throw new HuffException("Bad input");
			}
	
			if (bits == 0) {
				current = current.left;
			} else {
				current = current.right;
			}
	
			if (current.left == null && current.right == null) {
				if (current.value == PSEUDO_EOF) {
					break;
				} else {
					out.writeBits(BITS_PER_WORD, current.value);
					current = root;
				}
			}
		}
	
		out.close();
	}
	
	

	
}