class Tree
{
	public Tree left, right;
	public int depth;
	public int value;
	
	Tree(int d, int v)
	{
		left = null;
		right = null;
		depth = d;
		value = v;
	}
	
	public boolean equal(Tree t)
	{
		if (left == null && t.left == null) return value == t.value;
		else return (value == t.value && left.equal(t.left) && right.equal(t.right));
	}

	public boolean lessequal(Tree t)
	{
		if (left == null && t.left == null) return value <= t.value;
		else return (value <= t.value && left.lessequal(t.left) && right.lessequal(t.right));
	}

	public boolean less(Tree t)
	{
		return (lessequal(t) && !equal(t));
	}

	public Tree add(Tree t)
	{
		if (left == null && t.left == null)
		{
			Tree sum = new Tree(depth, value + t.value);
			return sum;
		}
		Tree sum = new Tree(depth, value + t.value);
		sum.left = left.add(t.left);
		sum.right = right.add(t.right);
		return sum;
		}

	public Tree subtree(boolean is_left)
	{
		if (is_left)
			return depth_dec(left);
		else
			return depth_dec(right);
	}

	public Tree depth_dec(Tree t)
	{
		if (t== null) return null;
		if (t.left == null)
			t.depth--;
		else
		{
			t.depth--;
			depth_dec(t.left);
			depth_dec(t.right);
		}
		return t;
	}
	
	public Tree depth_inc(Tree t)
	{
		if (t== null) return null;
		if (t.left == null)
			t.depth++;
		else
		{
			t.depth++;
			depth_inc(t.left);
			depth_inc(t.right);
		}
		return t;
	}

	public Tree del_highest_level(int k)
	{
		if (depth == k) return null;
		else
		{
			Tree l = null;
			if (left != null) l = left.del_highest_level(k);
			Tree r = null;
			if (right != null) r = right.del_highest_level(k);
			Tree t = new Tree(depth, value);
			t.left = l;
		    t.right = r;
		    return t;
		}
	}
	
	public void depthAdjust()
	{
		if (left != null)
		{
			left.depth = depth + 1;
			right.depth = depth + 1;
			left.depthAdjust();
			right.depthAdjust();
		}
	}
	
	public void print()
	{
		System.out.print(depth+" "+value+" | ");
		if (left != null)
		{
			left.print();
			right.print();
		}
		System.out.println("");
	}
}