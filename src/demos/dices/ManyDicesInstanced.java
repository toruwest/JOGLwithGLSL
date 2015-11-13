package demos.dices;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.FloatBuffer;
import java.util.Random;

import com.jogamp.opengl.DebugGL4;
import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GL2ES2;
import com.jogamp.opengl.GL4;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.GLCapabilities;
import com.jogamp.opengl.GLEventListener;
import com.jogamp.opengl.GLException;
import com.jogamp.opengl.GLProfile;
import com.jogamp.opengl.GLUniformData;
import com.jogamp.opengl.TraceGL4;
import com.jogamp.opengl.awt.GLCanvas;

import javax.swing.JFrame;
import javax.swing.SwingUtilities;

import com.jogamp.opengl.math.Matrix4;
import com.jogamp.opengl.util.FPSAnimator;
import com.jogamp.opengl.util.GLArrayDataServer;
import com.jogamp.opengl.util.PMVMatrix;
import com.jogamp.opengl.util.glsl.ShaderCode;
import com.jogamp.opengl.util.glsl.ShaderProgram;
import com.jogamp.opengl.util.glsl.ShaderState;
import com.jogamp.opengl.util.texture.Texture;
import com.jogamp.opengl.util.texture.TextureData;
import com.jogamp.opengl.util.texture.TextureIO;

public class ManyDicesInstanced implements GLEventListener {
//	private static final int ELEM_COUNT_PER_VERTEX = 3 + 3 + 2;
	private static final int ELEM_COUNT_PER_VERTEX = 3 + 2;
	private static final int VERTEX_COUNT = 6 * 3 * 2;

	private final JFrame frame;
	private final FPSAnimator animator;
	private final GLCanvas panel;
	private final Dimension dim = new Dimension(1024, 768);
	private float aspect;

	protected float winScale = 0.2f;
	private static final float WIN_SCALE_MIN = 1e-3f;
	private static final float WIN_SCALE_MAX = 100f;

	private static final int NO_OF_INSTANCE = 50;

	private static final String SHADER_BASE_NAME = "dice1";

	private ShaderState st;
	private GLArrayDataServer interleavedVBO;

	private PMVMatrix projectionMatrix;
	private GLUniformData projectionMatrixUniform;
	private GLUniformData transformMatrixUniform;
	private final FloatBuffer diceTransform = FloatBuffer.allocate(16 * NO_OF_INSTANCE);

	private final Matrix4[] mat = new Matrix4[NO_OF_INSTANCE];
	private final float[] axisX = new float[NO_OF_INSTANCE];
	private final float[] axisY = new float[NO_OF_INSTANCE];
	private final float[] axisZ = new float[NO_OF_INSTANCE];
	private final float[] rotationSpeed = new float[NO_OF_INSTANCE];

	private static final boolean useTraceGL = false;
	private static final boolean useTexture = true;
	private PrintStream stream;

	private TextureData textureData;
	private Texture  texture;
	private final int textureUnit = 0;

	private boolean isInitialized = false;

	public static void main(String[] args) {

		SwingUtilities.invokeLater(new Runnable() {
			@Override
			public void run() {
				new ManyDicesInstanced();
			}
		});
	}

	public ManyDicesInstanced() {
		if(useTraceGL) {
			try {
				stream = new PrintStream(new FileOutputStream(new File("dice-gl-trace.txt")));
			} catch (IOException e1) {
				e1.printStackTrace();
			}
		}

		initTransform();

		if(useTexture) {
			try {
				InputStream istream = new FileInputStream(new File("dice.jpg"));
				GLProfile glp = GLProfile.getMaxFixedFunc(true);
				textureData = TextureIO.newTextureData(glp, istream, false , TextureIO.JPG);
				if(textureData == null) {
					System.err.println("テクスチャ画像がロードできませんでした。");
					System.exit(-1);
				}
			} catch (IOException e1) {
				e1.printStackTrace();
			}
		}

		frame = new JFrame(this.getClass().getSimpleName());
		frame.setLayout(new BorderLayout());
		frame.addWindowListener(new WindowAdapter() {
			@Override
			public void windowClosing(WindowEvent e) {
				animator.stop();
				System.exit(0);
			}
		});
		panel = new GLCanvas(new GLCapabilities(GLProfile.get(GLProfile.GL4)));
		panel.addGLEventListener(this);

		frame.addMouseWheelListener(new MouseWheelListener() {

			@Override
			public void mouseWheelMoved(MouseWheelEvent e) {
				int step = e.getWheelRotation();
				if(step > 0) {
					winScale *= 1.05;
					if(winScale > WIN_SCALE_MAX) winScale = WIN_SCALE_MAX;
				} else if(0 > step ) {
					winScale *= 0.95;
					if(winScale < WIN_SCALE_MIN) winScale = WIN_SCALE_MIN;
				}
				//System.out.println("scale:" + winScale);
			}
		});

		panel.setPreferredSize(dim);
		frame.add(panel, BorderLayout.CENTER);
		frame.pack();
		frame.setVisible(true);
		animator = new FPSAnimator(panel, 60, true);
		animator.start();
	}

	private void initTransform() {
		Random rnd = new Random();
		for(int i = 0; i < NO_OF_INSTANCE; i++) {
			rotationSpeed[i] = 0.1f * rnd.nextFloat();
			mat[i] = new Matrix4();
			mat[i].loadIdentity();
			float scale = 1f;
			mat[i].scale(scale, scale, scale);
			//setup initial position of each dice
			mat[i].translate(30f * rnd.nextFloat() - 15f,
					20f * rnd.nextFloat() - 10f,
					20f * rnd.nextFloat() - 10f);
			axisX[i] = 2*rnd.nextFloat() - 1f;
			axisY[i] = 2*rnd.nextFloat() - 1f;
			axisZ[i] = 2*rnd.nextFloat() - 1f;
		}
	}

	@Override
	public void init(GLAutoDrawable drawable) {
		GL4 gl = drawable.getGL().getGL4();
		drawable.setGL(new DebugGL4(gl));
		if(useTraceGL) {
			drawable.setGL(new TraceGL4(gl, stream));
		}
        if(textureData != null) {
            this.texture = TextureIO.newTexture(gl, textureData);
        }

//		gl.glClearColor(1, 1, 1, 1); //white background
		gl.glClearColor(0, 0, 0, 1); //black background
		gl.glEnable(GL.GL_DEPTH_TEST);
		gl.glCullFace(GL.GL_BACK);
		gl.glClearDepth(1.0f);

		System.err.println("Chosen GLCapabilities: " + drawable.getChosenGLCapabilities());
		System.err.println("INIT GL IS: " + gl.getClass().getName());
		System.err.println("GL_VENDOR: " + gl.glGetString(GL4.GL_VENDOR));
		System.err.println("GL_RENDERER: " + gl.glGetString(GL4.GL_RENDERER));
		System.err.println("GL_VERSION: " + gl.glGetString(GL4.GL_VERSION));

		initShader(gl);
        projectionMatrix = new PMVMatrix();
		projectionMatrixUniform = new GLUniformData("mgl_PMatrix", 4, 4, projectionMatrix.glGetPMatrixf());
		st.ownUniform(projectionMatrixUniform);
        if(!st.uniform(gl, projectionMatrixUniform)) {
            throw new GLException("Error setting mgl_PMatrix in shader: " + st);
        }

        if(useTexture) {
        	if(!st.uniform(gl, new GLUniformData("mgl_ActiveTexture", textureUnit))) {
        		throw new GLException("Error setting mgl_ActiveTexture in shader: "+st);
        	}
        }

        transformMatrixUniform =  new GLUniformData("mgl_MVMatrix", 4, 4, diceTransform);

        st.ownUniform(transformMatrixUniform);
        if(!st.uniform(gl, transformMatrixUniform)) {
            throw new GLException("Error setting mgl_MVMatrix in shader: " + st);
        }

        initVBO(gl);

        if(useTexture && texture != null ) {
        	gl.glActiveTexture(GL.GL_TEXTURE0 + textureUnit);
            texture.enable(gl);
            texture.bind(gl);
        }

		isInitialized = true;
	}

    private void initShader(GL4 gl) {
        // Create & Compile the shader objects
        ShaderCode vp0 = ShaderCode.create(gl, GL2ES2.GL_VERTEX_SHADER, this.getClass(),
                                            "shaders", "shaders/bin", SHADER_BASE_NAME, true);
        ShaderCode fp0 = ShaderCode.create(gl, GL2ES2.GL_FRAGMENT_SHADER, this.getClass(),
                                            "shaders", "shaders/bin", SHADER_BASE_NAME, true);
        vp0.replaceInShaderSource("NO_OF_INSTANCE", String.valueOf(NO_OF_INSTANCE));

        vp0.defaultShaderCustomization(gl, true, true);
        fp0.defaultShaderCustomization(gl, true, true);

        vp0.dumpShaderSource(System.out);
        fp0.dumpShaderSource(System.out);

        // Create & Link the shader program
        ShaderProgram sp = new ShaderProgram();
        sp.add(vp0);
        sp.add(fp0);
        if(!sp.link(gl, System.err)) {
            throw new GLException("Couldn't link program: "+sp);
        }

        // Let's manage all our states using ShaderState.
        st = new ShaderState();
        st.attachShaderProgram(gl, sp, true);
    }

	@Override
	public void reshape(GLAutoDrawable drawable, int x, int y, int width, int height) {
		GL4 gl3 = drawable.getGL().getGL4();
		gl3.glViewport(0, 0, width, height);
		aspect = (float) width / (float) height;

		projectionMatrix.glMatrixMode(GL2.GL_PROJECTION);
		projectionMatrix.glLoadIdentity();
		projectionMatrix.gluPerspective(45, aspect, 1f, 20000f);
		projectionMatrix.gluLookAt(0, 0, -10, 0, 0, 0, 0, 1, 0);
	}

	@Override
	public void display(GLAutoDrawable drawable) {
		if(!isInitialized ) return;

		GL4 gl = drawable.getGL().getGL4();
		gl.glClear(GL4.GL_COLOR_BUFFER_BIT | GL4.GL_DEPTH_BUFFER_BIT);

		st.useProgram(gl, true);
		projectionMatrix.glMatrixMode(GL2.GL_PROJECTION);

		projectionMatrix.glPushMatrix();
		projectionMatrix.glScalef(winScale, winScale, winScale);
//		projectionMatrix.update();
		st.uniform(gl, projectionMatrixUniform);
		projectionMatrix.glPopMatrix();

		generateDiceTransform();
		st.uniform(gl, transformMatrixUniform);
		interleavedVBO.enableBuffer(gl, true);
        if(texture != null) {
            gl.glActiveTexture(GL.GL_TEXTURE0 + textureUnit);
            texture.enable(gl);
            texture.bind(gl);
        }

		gl.glDrawArraysInstanced(GL4.GL_TRIANGLES, 0, VERTEX_COUNT, NO_OF_INSTANCE);

		interleavedVBO.enableBuffer(gl, false);
        if(texture != null) {
        	texture.disable(gl);
        }

		st.useProgram(gl, false);
	}

	private void generateDiceTransform() {
		diceTransform.clear();
		for(int i = 0; i < NO_OF_INSTANCE; i++) {
			mat[i].rotate(rotationSpeed[i], axisX[i], axisY[i], axisZ[i]);
			diceTransform.put(mat[i].getMatrix());
		}
		diceTransform.rewind();
	}

	@Override
	public void dispose(GLAutoDrawable drawable){
		GL4 gl = drawable.getGL().getGL4();
		st.destroy(gl);
	}

	//さいころのような立方体にテクスチャを貼る場合には、頂点indexは使わない。
	//なので、glDrawElements()ではなくglDrawArrays()を使う。
	//さいころの各面を２つの三角で描画するので、三角の数は2x6 = 12になる。
	//頂点データは共有せず、各面ごとに別の頂点とする。従って、頂点の数は36となる。
	private void initVBO(GL4 gl) {
		interleavedVBO = GLArrayDataServer.createGLSLInterleaved(ELEM_COUNT_PER_VERTEX, GL.GL_FLOAT, false, VERTEX_COUNT, GL.GL_STATIC_DRAW);
		interleavedVBO.addGLSLSubArray("mgl_Vertex", 3, GL.GL_ARRAY_BUFFER);
//		interleavedVBO.addGLSLSubArray("mgl_Color",  3, GL.GL_ARRAY_BUFFER);
		interleavedVBO.addGLSLSubArray("mgl_MultiTexCoord", 2, GL.GL_ARRAY_BUFFER);

		FloatBuffer ib = (FloatBuffer)interleavedVBO.getBuffer();
		int texIndex = 0;
		for(int i = 0; i < cubeIndices.length; i++) {
			for(int j = 0; j < cubeIndices[i].length; j++) {
				for(int k = 0; k < cubeIndices[i][j].length; k++) {
					//以下は0-7
					int index = cubeIndices[i][j][k];
					System.out.println("index:" + index);
					float[] vertices = cubeVertex[index];
					ib.put(vertices);
//					//面毎に色を割り当てる。
//					float[] color = colors[i];
//					ib.put(color);
					ib.put(texcoord[texIndex++]);
				}
			}
		}

		dump(ib);
		interleavedVBO.seal(gl, true);
		interleavedVBO.enableBuffer(gl, false);
		st.ownAttribute(interleavedVBO, true);
		st.useProgram(gl, false);
	}

	private void dump(FloatBuffer ib) {
		for(int i = 0; i < ib.capacity(); i++) {
			System.out.print(ib.get(i));
			System.out.print(", ");
			if(i % ELEM_COUNT_PER_VERTEX == ELEM_COUNT_PER_VERTEX-1) System.out.println();
		}
		System.out.println();
	}

	protected  float cubeVertex[][] = {
			{ -1f, -1f, -1f }, // A
			{  1f, -1f, -1f }, // B
			{  1f,  1f, -1f }, // C
			{ -1f,  1f, -1f }, // D
			{ -1f, -1f,  1f }, // E
			{  1f, -1f,  1f }, // F
			{  1f,  1f,  1f }, // G
			{ -1f,  1f,  1f }  // H
	};

	// http://www.wakayama-u.ac.jp/~tokoi/opengl/libglut.html#8の8.2にある図を参照。
	//床井先生のオリジナルでは右回り（時計回り）になっていたが、以下は左回りに直してある。
	//OpenGLではデフォルトは左回りを表面として扱い、右回りの場合、glFrontFace(GL2.GL_CW)を指定する必要がある。
	//また、OpenGL3.X以降ではGL_QUADは廃止されたのでGL_TRIANGLEで使えるように四角を２つの三角に分割する。
	//以下はCubeRendererByDrawArraysクラスで検証済み
	//要素の最大数が8なのは頂点の数だから。
	private final int[][][] cubeIndices = {
			{{ 0, 3, 2 }, { 0, 2, 1 }},
			{{ 1, 2, 6 }, { 1, 6, 5 }},
			{{ 5, 6, 7 }, { 5, 7, 4 }},
			{{ 4, 7, 3 }, { 4, 3, 0 }},
			{{ 6, 2, 3 }, { 6, 3, 7 }},
			{{ 1, 5, 4 }, { 1, 4, 0 }}
	};

	// 頂点のテクスチャ座標 36個必要になる。(各面に3*2、* 6面)
	// http://marina.sys.wakayama-u.ac.jp/~tokoi/?date=20050610
	//ただし、上のURLではGL_QUADで使うための座標だが、ここではGL_TRIANGLESで描画するので、一つの面は２つの三角で構成される。
	//サイコロは反対の面の合計が７になっていなければならないので、順番を入れ替えている。
	protected  float texcoord[][] = {
			//1
			{0.0f, 1f},{0.125f, 1f},{0.125f, 0f},
			{0.0f, 1f},{0.125f, 0f},{0.0f, 0f},
			//2
			{0.125f, 1f},{0.25f, 1f},{0.25f, 0f},
			{0.125f, 1f},{0.25f, 0f},{0.125f, 0f},
			//6
			{0.625f, 1f},{0.75f, 1f},{0.75f, 0f},
			{0.625f, 1f},{0.75f, 0f},{0.625f, 0f},
			//4
			{0.375f, 1f},{0.5f, 1f},{0.5f, 0f},
			{0.375f, 1f},{0.5f, 0f},{0.375f, 0f},
			//3
			{0.25f, 1f},{0.375f, 1f},{0.375f, 0f},
			{0.25f, 1f},{0.375f, 0f},{0.25f, 0f},
			//5
			{0.5f, 1f},{0.625f, 1f},{0.625f, 0f},
			{0.5f, 1f},{0.625f, 0f},{0.5f, 0f},
	};

	//これはたぶんcubeFaceの順番と一致している必要があるのでは？
	protected float[][] cubeNorm = {
      		{ 0f, 0f,-1f}, //back(6)
      		{ 1f, 0f, 0f}, //right(2)
    		{ 0f, 0f, 1f}, //front(1)
    		{-1f, 0f, 0f}, //left(5)
    		{ 0f,-1f, 0f}, //bottom(3)
    		{ 0f, 1f, 0f}, //top(4)
    };

//	//面毎に色を割り当てる。
//	private final float[][] colors = {
//			{0f, 1f, 0f},
//			{1f, 0f, 0f},
//			{0f, 0f, 1f},
//			{1f, 1f, 0f},
//			{1f, 0f, 1f},
//			{0f, 1f, 1f},
//	};
}
